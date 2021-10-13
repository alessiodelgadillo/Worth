import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class ServerWORTH {

    /* OVERVIEW: modella il server del servizio WORTH
     *              - users: insieme degli utenti (con le relative informazioni) registrati al servizio
     *              - projects: insieme dei progetti presenti nel server
     *              - registerManager: oggetto che fornisce metodi remoti al client  */

    private final List<User> users;
    private final LinkedList<Project> projects;
    private RegisterManager registerManager;

    private String multicastIP = "239.0.0.0";
    private final LinkedList<String> oldAddress;


    private final static int BUFFER_DIMENSION = 1024;
    private final static int REGISTER_PORT = 4567;
    private final static int TCP_PORT = 5678;


                                        //METODO COSTRUTTORE

    public ServerWORTH() {
        this.oldAddress = new LinkedList<>();
        this.users = Collections.synchronizedList(new ArrayList<>());
        this.projects = new LinkedList<>();
        try {
            readJSon();
        } catch (IOException e) {
            System.out.println("Impossibile leggere i file di recovery");
        }

    }

    //-------------------------------------------------------------------------------------//

                                        //METODI D'ISTANZA

    // EFFECTS: esporta l'oggetto "registerManager"
    public void registerService() {
        try {
            registerManager = new RegisterManager(users);
            RegisterManagerInterface stub = (RegisterManagerInterface) UnicastRemoteObject.exportObject(registerManager, 0);
            LocateRegistry.createRegistry(REGISTER_PORT);
            Registry r = LocateRegistry.getRegistry(REGISTER_PORT);
            r.rebind("REGISTER-MANAGER", stub);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    //EFFECTS: avvia il server WORTH
    public void start() {

        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            ServerSocket serverSocket = serverChannel.socket();
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), TCP_PORT));
            serverChannel.configureBlocking(false);

            Selector selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Listening for connections");

            while (true) {
                if (selector.select() == 0) continue;

                //insieme delle chiavi
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectionKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    try { //try usato per gestire la terminazione improvvisa del client
                        if (key.isAcceptable()) {       //ACCETTABLE

                            ServerSocketChannel server = (ServerSocketChannel) key.channel();
                            SocketChannel socketChannel = server.accept();
                            System.out.println("New connection from: " + socketChannel.getRemoteAddress());

                            socketChannel.configureBlocking(false);
                            this.registerRead(selector, socketChannel);

                        } else if (key.isReadable()) {  //READABLE
                            this.readMessage(selector, key);

                        } else if (key.isWritable()) {  //WRITEABLE
                            this.writeMessage(selector, key);
                        }
                        
                    }catch (IOException e){
                        //client terminato improvvisamente
                        System.out.println("Client is terminated");
                        key.channel().close();
                        key.cancel();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //-------------------------------------------------------------------------------------//

    /* EFFECTS: registra l'interesse all'operazione di read sul selettore
     * THROWS: IOException se si verifica un errore di I/O */
    private void registerRead(Selector selector, SocketChannel socketChannel) throws IOException {
        //creazione del buffer
        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        ByteBuffer message = ByteBuffer.allocate(BUFFER_DIMENSION);
        ByteBuffer[] buffers = {length, message};

        /* aggiunge il canale del client al selector con l'operazione OP_READ
         * e aggiunge l'array ByteBuffer [length, message] come attachment  */
        socketChannel.register(selector, SelectionKey.OP_READ, buffers);
    }

    /* EFFECTS: legge ed esegue la richiesta del client e registra l'interesse
     *           all'operazione di write sul selettore
     * THROWS: IOException se si verifica un errore di I/O */
    private void readMessage(Selector selector, SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        //recupera l'array di ByteBuffer
        ByteBuffer[] buffers = (ByteBuffer[]) key.attachment();
        socketChannel.read(buffers);

        if (!buffers[0].hasRemaining()) {
            buffers[0].flip();
            int length = buffers[0].getInt();

            if (buffers[1].position() == length) {
                buffers[1].flip();

                String message = new String(buffers[1].array(), StandardCharsets.UTF_8);

                //ottengo l'indirizzo IP e la porta del client
                Socket socket = socketChannel.socket();
                InetAddress address = socket.getInetAddress();
                int port = socket.getPort();

                //esegue la richiesta del client
                String answer = runCmd(message, address, port);
                ByteBuffer buffer = ByteBuffer.wrap(answer.getBytes(StandardCharsets.UTF_8));

                //aggiunge il canale del lient al selector con l'operazione OP_WRITE
                socketChannel.register(selector, SelectionKey.OP_WRITE, buffer);
            }
        }
    }

    /* EFFECTS: scrive la risposta sul canale del client
     * THROWS:  IOException se si verifica un errore di I/O*/
    private void writeMessage(Selector selector, SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        while (buffer.hasRemaining()) socketChannel.write(buffer);
        buffer.clear();
        this.registerRead(selector, socketChannel);
    }

    /* EFFECTS: esegue il comando ricevuto dal client
                e restituisce il messaggio di risposta */
    private String runCmd(String message, InetAddress address, int port) {
        String[] strings = message.split(" ");
        String cmd = strings[0].trim();
        switch (cmd) {
            case "login": {
                try {
                    this.login(strings[1], strings[2].trim(), address, port);
                    //esegue una callback per informare gli altri utenti del login
                    registerManager.update(listToMap(users));
                    return strings[1] + " logged in";
                } catch (Exception e) {
                    return e.getMessage();
                }
            }
            case "logout": {
                try {
                    User user = getUser(address, port);
                    if (user == null) throw new NoSuchElementException("Username not found");
                    this.logout(user);
                    //esegue una callback per informare gli altri utenti del logout
                    registerManager.update(listToMap(users));
                    return user.getName() + " logged out";
                } catch (Exception e) {
                    return e.getMessage();
                }
            }
            case "list_projects": {
                try {
                    String nickUtente = getUser(address, port).getName();
                    return this.listProjects(nickUtente);
                } catch (Exception e) {
                    return e.getMessage();
                }
            }
            case "create_project": {
                try {
                    String creator = getUser(address, port).getName();
                    this.createProject(strings[1].trim(), creator);
                    return "Project " + strings[1].trim() + " created";
                } catch (Exception e) {
                    return e.getMessage();
                }
            }
            case "add_member": {
                try {
                    if (hasRights(strings[1], address, port)) {
                        this.addMember(strings[1], strings[2].trim());
                        return strings[2].trim() + " added to " + strings[1];
                    } else return "Access denied";
                } catch (Exception e) {
                    return e.getMessage();
                }
            }
            case "show_members": {
                try {
                    //controllo sui diritti di accesso al progetto
                    if (hasRights(strings[1].trim(), address, port)) return this.showMembers(strings[1].trim());
                    else return "Access denied";
                } catch (Exception e) {
                    return e.getMessage();
                }
            }
            case "show_cards": {
                try {
                    if (hasRights(strings[1].trim(), address, port)) return this.showCards(strings[1].trim());
                    else return "Access denied";
                } catch (Exception e) {
                    return e.getMessage();
                }
            }
            case "show_card": {
                try {
                    if (hasRights(strings[1], address, port)) return this.showCard(strings[1], strings[2].trim());
                    else return "Access denied";
                } catch (Exception e) {
                    return e.getMessage();
                }
            }
            case "add_card": {
                try {
                    if (hasRights(strings[1], address, port)) {
                        this.addCard(strings[1], strings[2], createDescription(strings));
                        return "Card " + strings[2] + " added to " + strings[1];
                    } else return "Access denied";
                } catch (Exception e) {
                    return e.getMessage();
                }
            }
            case "move_card": {
                try {
                    if (hasRights(strings[1], address, port)) {
                        this.moveCard(strings[1], strings[2], strings[3], strings[4].trim());
                        return "Card " + strings[2] + " of project " + strings[1] + " moved from " + "\"" + strings[3] + "\"" + " to " + "\"" + strings[4].trim() + "\"";
                    } else return "Access denied";
                } catch (Exception e) {
                    return e.getMessage();
                }
            }
            case "get_card_history": {
                try {
                    if (hasRights(strings[1], address, port)) return this.getCardHistory(strings[1], strings[2].trim());
                    else return "Access denied";
                } catch (Exception e) {
                    return e.getMessage();
                }
            }
            case "cancel_project": {
                try {
                    if (hasRights(strings[1].trim(), address, port)) {
                        this.cancelProject(strings[1].trim());
                        return "Project " + strings[1].trim() + " cancelled";
                    } else return "Access denied";
                } catch (Exception e) {
                    return e.getMessage();
                }
            }
            case "join_chat":{
                try {
                    if (hasRights(strings[1].trim(), address, port)) {
                        return this.joinChat(strings[1].trim());
                    } else return "Access denied";
                }catch (Exception e ){
                    return e.getMessage();
                }
            }
            default : {
                return "Command not found";
            }
        }
    }

    /* REQUIRES: nickUtente != null && password != null && address != null && port >= 0
     * EFFECTS: esegue il login dell'utente
     * THROWS: - NullPointerException se nickUtente == null || password == null || address == null
     *         - IllegalArgumentException se port < 0
     *         - NoSuchElementException se lo username non risulta registrato
     *         - ExistingNameException se lo username risulta già online
     *         - WrongPswException se la password non è corretta */
    private void login(String nickUtente, String password, InetAddress address, int port) throws Exception {
        if (nickUtente == null) throw new NullPointerException("Invalid username");
        if (password == null) throw new NullPointerException("Invalid password");
        if (address == null) throw new NullPointerException("Invalid address");
        if (port < 0) throw new IllegalArgumentException("Invalid port number");

        User user = getUser(nickUtente);
        if (user == null) throw new NoSuchElementException("Username not found");
        if (user.getPassword().equals(password)) {
            if (user.getState().equals(UserState.ONLINE)) throw new ExistingNameException("User already logged");
            user.setState(UserState.ONLINE);
            user.setAddress(address);
            user.setPort(port);

        } else throw new WrongPswException("Wrong Password");

    }

    //EFFECTS: esegue il logout dell'utente
    private void logout(User nickUtente) {
        nickUtente.setState(UserState.OFFLINE);
        nickUtente.setAddress(null);
        nickUtente.setPort(-1);
    }

    /* REQUIRES: nickUtente != null
     * EFFECTS: mostra i progetti di cui nickUtente è membro
     * THROWS: NullPointerException se nickUtente == null || se nickUtente non fa parte di nessun progetto */
    private String listProjects(String nickUtente) {
        if (nickUtente == null) throw new NullPointerException("Invalid username");
        StringBuilder list = new StringBuilder();
        for (Project project : projects) {
            if (project.isMember(nickUtente))
                list.append(project.getName()).append(System.lineSeparator());
        }
        if  (list.toString().length()==0) throw new NullPointerException("At the moment there is no project"+System.lineSeparator());
        return list.toString();
    }

    /* REQUIRES: projectName != null
     * EFFECTS: crea un nuovo progetto con nome "projectName"
     * THROWS: - NullPointerException se projectName == null
     *         - ExistingNameException se esiste già un progetto con quel nome */
    private void createProject(String projectName, String creator) throws ExistingNameException {
        if (projectName == null) throw new NullPointerException("Invalid project name");
        if (getProject(projectName) != null) throw new ExistingNameException("Project already exists");
        Project project = new Project(projectName, creator, newMulticastIP());
        projects.add(project);
    }

    /* REQUIRES: projectName != null && nickUtente != null
     * EFFECTS: aggiunge l'utente indicato come membro del progetto indicato
     * THROWS: - NullPointerException se projectName == null || nickUtente == null
     *         - NoSuchElementException se non esiste alcun progetto con nome <projectName> ||
     *           se non esiste alcun utente con nome <nickUtente>*/
    private void addMember(String projectName, String nickUtente) throws ExistingNameException {
        if (projectName == null) throw new NullPointerException("Invalid project name");
        if (nickUtente == null) throw new NullPointerException("Invalid username");

        Project project = getProject(projectName);
        if (project == null) throw new NoSuchElementException("Project not found");
        String user = getUser(nickUtente).getName();
        if (user == null) throw new NoSuchElementException("Username not found");
        project.addMember(user);
    }

    /* REQUIRES: projectName != null
     * EFFECTS: mostra i membri di un progetto
     * THROWS: - NullPointerException se projectName == null
     *         - NoSuchElementException se il progetto indicato da <projectName> non esiste */
    private String showMembers(String projectName) {
        if (projectName == null) throw new NullPointerException("Invalid project name");
        Project project = getProject(projectName);
        if (project == null) throw new NoSuchElementException("Project not found");
        return project.showMembers();
    }

    /* REQUIRES: projectName != null
     * EFFECTS: mostra tutte le card di un progetto
     * THROWS: - NullPointerException se projectName == null
     *         - NoSuchElementException se il progetto indicato da <projectName> non esiste */
    private String showCards(String projectName) {
        if (projectName == null) throw new NullPointerException("Invalid project name");
        Project project = getProject(projectName);
        if (project == null) throw new NoSuchElementException("Project not found\n");
        return project.showCards();
    }

    /* REQUIRES: projectName != null
     * EFFECTS: mostra la card <cardName> del progetto <projectName>
     * THROWS: - NullPointerException se projectName == null
     *         - NoSuchElementException se il progetto indicato da <projectName> non esiste  */
    private String showCard(String projectName, String cardName) {
        if (projectName == null) throw new NullPointerException("Invalid project name");
        Project project = getProject(projectName);
        if (project == null) throw new NoSuchElementException("Project not found");
        return project.showCard(cardName);
    }

    /* REQUIRES: projectName != null
     * EFFECTS: aggiunge la card <cardName> al progetto <projectName>
     * THROWS: - NullPointerException se projectName == null
     *         - NoSuchElementException se il progetto indicato da <projectName> non esiste
     *         - ExistingNameException se esiste già una card con il nome indicato */
    private void addCard(String projectName, String cardName, String descrizione) throws ExistingNameException {
        if (projectName == null) throw new NullPointerException("Invalid project name");
        Project project = getProject(projectName);
        if (project == null) throw new NoSuchElementException("Project not found");
        project.addCard(cardName, descrizione);
    }

    /* REQUIRES: projectName != null
     * EFFECTS: sposta la card <cardName> del progetto <projectName> da <listaPartenza> a <listaDestinazione>
     * THROWS: - NullPointerException se projectName == null
     *         - NoSuchElementException se il progetto indicato da <projectName> non esiste */
    private void moveCard(String projectName, String cardName, String listaPartenza, String listaDestinazione) {
        if (projectName == null) throw new NullPointerException("Invalid project name");
        Project project = getProject(projectName);
        if (project == null) throw new NoSuchElementException("Project not found");
        project.moveCard(cardName, listaPartenza.toLowerCase(), listaDestinazione.toLowerCase());
    }

    /* REQUIRES: projectName != null
     * EFFECTS: ottine la "storia" della card <cardName> del progetto <projectName>
     * THROWS: - NullPointerException se projectName == null
     *         - NoSuchElementException se il progetto indicato da <projectName> non esiste */
    private String getCardHistory(String projectName, String cardName) {
        if (projectName == null) throw new NullPointerException("Invalid project name");
        Project project = getProject(projectName);
        if (project == null) throw new NoSuchElementException("Project not found");
        return project.getCardHistory(cardName);

    }

    /* REQUIRES: projectName != null
     * EFFECTS: elimina la card <cardName> dal progetto <projectName>
     * THROWS: - NullPointerException se projectName == null
     *         - NoSuchElementException se il progetto indicato da <projectName> non esiste
     *         - IllegalArgumentException se tutte le card del progetto non sono nella lista "done" */
    private void cancelProject(String projectName) {
        if (projectName == null) throw new NullPointerException("Invalid project name");
        Project project = getProject(projectName);
        if (project == null) throw new NoSuchElementException("Project not found");
        if (project.isDone()) {
            project.sendMessage("close");
            oldAddress.add(project.getGroup().getHostAddress());
            projects.remove(project);
            deleteProjectCopy(project);
        }
        else throw new IllegalArgumentException("The project can be cancel if all the cards have been done");
    }

    /* REQUIRES: projectName != null
     * EFFECTS: restituisce l'indirizzo di multicast del progetto <projectName>
     * THROWS: - NullPointerException se projectName == null
     *         - NoSuchElementException se il progetto indicato da <projectName> non esiste */
    private String joinChat (String projectName) {
        if (projectName == null) throw new NullPointerException("Invalid project name");
        Project project = getProject(projectName);
        if (project == null) throw new NoSuchElementException("Project not found");
        else return project.getGroup().getHostAddress() + " " + project.getPort();
    }

    //-------------------------------------------------------------------------------------//

                                        //METODI AUSILIARI

    /* EFFECTS: cerca e restituisce il progetto indicato da <projectName>;
     *          restituisce null se tale progetto non esiste */
    private Project getProject(String projectName) {
        for (Project project : projects) {
            if (project.getName().equals(projectName))
                return project;
        }
        return null;
    }

    /* EFFECTS: cerca e restituisce l'utente indicato da <nickUtente>;
     *          restituisce null se tale utente non esiste */
    private User getUser(String nickUtente) {
        for (User user : users) {
            if (user.getName().equals(nickUtente))
                return user;
        }
        return null;
    }

    /* EFFECTS: cerca e restituisce l'utente indicato dalla coppia <address, port>;
     *          restituisce null se tale utente non esiste */
    private User getUser(InetAddress address, int port) {
        for (User user : users) {
            if (user.getState().equals(UserState.ONLINE) && user.getAddress().equals(address) && user.getPort() == port)
                return user;
        }
        return null;
    }

    /* EFFECTS: restituisce true se il l'utente indicato dalla coppia <address, port>
     *          ha i diritti di accesso per il progetto <projectName>, false altrimenti
     * THROWS: - NullPointerException se il progetto non esiste */
    private boolean hasRights (String projectName, InetAddress address, int port){
        Project project = getProject(projectName);
        if (project == null) throw new NullPointerException("Project not found");
        String user = getUser(address, port).getName();
        ArrayList<String> members = project.getMembers();
        return members.contains(user);
    }

    //EFFECTS: genera un nuovo indirizzo IP multicast a partire dall'ultimo indirizzo usato
    private String newMulticastIP () {
        if (!oldAddress.isEmpty()) {
            String out = oldAddress.getFirst();
            oldAddress.removeFirst();
            return out;
        }
        String[] bytes = multicastIP.split("\\.");
        int[] intBytes = new int[4];
        for (int i = 0; i < 4; i++) {
            intBytes[i] = Integer.parseInt(bytes[i]);
        }
        boolean stop = false;
        int index = intBytes.length - 1;
        do {
            if (intBytes[index] < 255) {
                intBytes[index]++;
                stop = true;
            } else {
                intBytes[index] = 0;
                index--;
            }
        } while (!stop);

        String out = intBytes[0] + "." + intBytes[1] + "." + intBytes[2] + "." + intBytes[3];
        multicastIP = out;
        return out;
    }

    //EFFECTS: svuota la directory di un progetto e la elimina
    private void deleteProjectCopy (Project project){
        File projectDir = new File("./Recovery" + File.separator + project.getName());
        if (projectDir.exists()) {
            File[] files = projectDir.listFiles();
            for (File file: files) {
                if (file.delete()) System.out.println(file.getName() + " deleted");
            }
            if (projectDir.delete()) System.out.println("Directory " + project.getName() + " deleted");
        }
    }

    /* EFFECTS: recupera lo stato della sessione precedente leggendo i contenuti della directory "Recovery"
     * THROWS: IOException se si verifica un errore di I/O */
    public void readJSon () throws IOException {
        File root = new File("./Recovery");
        if (!root.exists()) return;
        File[] files = root.listFiles();
        if (files == null || files.length == 0) return;
        ObjectMapper objectMapper = new ObjectMapper();

        //directory principale
        for (File file: files){
            if (file.isDirectory()){
                File[] files1 = file.listFiles();
                if (files1 == null || files1.length == 0) continue;

                //directory del progetto
                Project project = new Project(file.getName(), newMulticastIP());
                for (File file1: files1){
                    FileInputStream fileInputStream = new FileInputStream(file1);
                    FileChannel inChannel = fileInputStream.getChannel();
                    ByteBuffer buffer = ByteBuffer.allocate((int) inChannel.size());
                    inChannel.read(buffer);

                    //file dei membri del progetto
                    if (file1.getName().equals("members.json")){
                        project.setMembers(objectMapper.reader().forType(new TypeReference<ArrayList<String>>() {
                        }).readValue(buffer.array()));
                    }
                    //file della card del progetto
                    else{
                        Card card = objectMapper.readValue(buffer.array(), Card.class);
                        int lastState = card.getStory().size()-1;
                        switch (card.getStory().get(lastState)){
                            case ToDo:project.getToDo().add(card); break;
                            case InProgress:project.getInProgress().add(card); break;
                            case ToBeRevised:project.getToBeRevised().add(card); break;
                            case Done:project.getDone().add(card); break;
                        }
                    }
                    inChannel.close();
                    fileInputStream.close();
                }
                projects.add(project);
                //file di registrazione
            } else {
                FileInputStream fileInputStream = new FileInputStream(file);
                FileChannel inChannel = fileInputStream.getChannel();
                ByteBuffer buffer = ByteBuffer.allocate((int) inChannel.size());
                inChannel.read(buffer);
                users.addAll (objectMapper.reader().forType(new TypeReference<ArrayList<User>>() {
                }).readValue(buffer.array()));
                inChannel.close();
                fileInputStream.close();
                for (User user : users) {
                    user.setState(UserState.OFFLINE);
                }
            }
        }
    }

    //-------------------------------------------------------------------------------------//

                                        //FUNZIONI AUSILIARIE

    //"ricompatta" la descrizione di una card dopo l'invocazione del metodo split()
    private static String createDescription (String[] strings){
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 3; i < strings.length; i++) {
            stringBuilder.append(strings[i]).append(" ");
        }
        return stringBuilder.toString().trim();

    }

    //trasforma l'insieme di utenti in un'HashMap usata per condividere solo le informazioni essenziali
    private static HashMap<String, String> listToMap (List<User> users){

        HashMap<String, String> usersMap = new HashMap<>();
        for (User user: users) {
            usersMap.put(user.getName(), user.getState().toString());
        }
        return usersMap;

    }

}
