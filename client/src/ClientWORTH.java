import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class ClientWORTH {

    /* OVERVIEW: modella il client del servizio WORTH
     *              - users: collezione che mantiene l'associazione <username, stato>
     *              - remoteRegisterInterface: interfaccia dell'oggetto remoto usato per eseguire
     *                  la registrazione al servizio WORTH
     *              - notifyManager: oggetto che viene esportato per permettere le callback
     *              - stub: stub dell'oggetto remoto  */

    private final static int BUFFER_DIMENSION = 1024;
    private final static int REGISTER_PORT = 4567;
    private final static int TCP_PORT = 5678;

    private final HashMap<String, String> users;
    private final HashMap<String, Chat> chats;

    private RegisterManagerInterface remoteRegisterManager;
    private NotifyManagerInterface stub;
    private NotifyManager notifyManager;

                                        //METODO COSTRUTTORE

    public ClientWORTH() {
        this.users = new HashMap<>();
        this.chats = new HashMap<>();
    }

    //-------------------------------------------------------------------------------------//

                                        //METODI D'ISTANZA

    /* EFFECTS: recupera l'oggetto remoto esportato dal server
     *          ed esporta "notifyManager" per ricevere le callback */
    public void registerToService () throws RemoteException, NotBoundException {
        Registry r = LocateRegistry.getRegistry(REGISTER_PORT);
        remoteRegisterManager = (RegisterManagerInterface) r.lookup("REGISTER-MANAGER");
        notifyManager = new NotifyManager(users);
        stub = (NotifyManagerInterface) UnicastRemoteObject.exportObject(notifyManager, 0);
    }

    //proporre comando help

    // EFFECTS: avvia il client permettendo la comunicazione con il server WORTH
    public void start() {

        boolean exit = false;
        boolean logged = false;
        boolean connected = false;

        Scanner sc = new Scanner(System.in);

        String username = null;
        SocketChannel client = null;
        try {

            do {
                System.out.println("Sign up or login");


                /* finché l'utente non è loggato permetto solo di registrarsi,
                   eseguire il login o terminare il programma*/

                while (!logged) {
                    System.out.print("> ");
                    String cmdLine = sc.nextLine();
                    String[] cmd = cmdLine.split(" ");

                    //eseguo il controllo sulla linea di comando
                    if (cmd.length > 0) {
                        switch (cmd[0]) {
                            case "register" : {
                                if (cmd.length != 3) {
                                    System.out.println("Use: register <username> <password>");
                                } else {
                                    try {
                                        remoteRegisterManager.register(cmd[1], cmd[2].trim());
                                        System.out.println("Utente " + cmd[1] + " registrato");

                                    } catch (Exception e) {
                                        System.out.println(e.getMessage());
                                    }
                                }
                                break;
                            }
                            case "login" : {
                                if (cmd.length != 3) {
                                    System.out.println("Use: login <username> <password>");
                                } else {
                                    //se non è ancora stata aperta una connessione TCP ne apro una
                                    if (!connected) {
                                        client = SocketChannel.open(new InetSocketAddress(InetAddress.getLoopbackAddress(), TCP_PORT));
                                        client.configureBlocking(true);
                                        connected = true;
                                    }

                                    String message = sendCmd(client, cmdLine);
                                    if (message.contains("logged in")) {
                                        logged = true;
                                        username = cmd[1];
                                    }
                                    System.out.println(message.trim());
                                }
                                break;
                            }
                            case "exit" : {
                                sc.close();
                                UnicastRemoteObject.unexportObject(notifyManager, false);
                                if (client != null) client.close();
                                return;
                            }
                            case "help":{
                                help();
                                break;
                            }
                            default :
                                System.out.println("You have to login");
                                break;
                        }
                    }
                }
                //l'utente ha eseguito il login e adesso si registra al servizio di callback
                try {
                    remoteRegisterManager.registerForCallback(stub);
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return;
                }

                //permetto l'esecuzione degli altri comandi
                while (logged) {
                    System.out.print("> ");
                    String cmdLine = sc.nextLine();
                    String[] cmd = cmdLine.split(" ");
                    //controllo della linea di comando
                    if (cmd.length > 0) {
                        switch (cmd[0]) {
                            case "logout" : {
                                if (cmd.length != 1) {
                                    System.out.println("Use: logout");
                                } else {
                                    remoteRegisterManager.unregisterForCallback(stub);
                                    String message = sendCmd(client, cmdLine);
                                    System.out.println(message.trim());

                                    username = null;
                                    logged = false;
                                    for (Chat chat: chats.values()) {
                                        chat.close();
                                    }
                                    chats.clear();
                                    connected = false;
                                    client.close();
                                }
                                break;
                            }
                            case "list_users" : {
                                if (cmd.length != 1) {
                                    System.out.println("Use: list_users");
                                } else {
                                    if (!users.isEmpty()) {
                                        for (String name : users.keySet()) {
                                            System.out.println(name + " " + users.get(name));
                                        }
                                    }
                                }
                                break;
                            }
                            case "list_online_users" : {
                                if (cmd.length != 1) {
                                    System.out.println("Use: list_online_users");
                                } else {
                                    for (String name : users.keySet()) {
                                        String state = users.get(name);
                                        if (state.equals("ONLINE"))
                                            System.out.println(name + " " + state);
                                    }
                                }
                                break;
                            }
                            case "list_projects" : {
                                if (cmd.length != 1) {
                                    System.out.println("Use: list_projects");
                                } else {
                                    String message = sendCmd(client, cmdLine);
                                    System.out.println( message.trim());
                                }
                                break;
                            }
                            case "create_project" : {
                                if (cmd.length != 2) {
                                    System.out.println("Use: create_project <projectName>");
                                } else {
                                    String message = sendCmd(client, cmdLine);
                                    System.out.println(message.trim());
                                }
                                break;
                            }
                            case "add_member" : {
                                if (cmd.length != 3) {
                                    System.out.println("Use: add_member <projectName> <username>");
                                } else {
                                    String message = sendCmd(client, cmdLine);
                                    System.out.println(message.trim());
                                }
                                break;
                            }
                            case "show_members" : {
                                if (cmd.length != 2) {
                                    System.out.println("Use: show_members <projectName>");
                                } else {
                                    String message = sendCmd(client, cmdLine);
                                    System.out.println(message.trim());
                                }
                                break;
                            }
                            case "show_cards" : {
                                if (cmd.length != 2) {
                                    System.out.println("Use: show_cards <projectName>");
                                } else {
                                    String message = sendCmd(client, cmdLine);
                                    System.out.println(message.trim());
                                }
                                break;
                            }
                            case "show_card" : {
                                if (cmd.length != 3) {
                                    System.out.println("Use: show_card <projectName> <cardName>");
                                } else {
                                    String message = sendCmd(client, cmdLine);
                                    System.out.println(message.trim());
                                }
                                break;
                            }
                            case "add_card" : {
                                if (cmd.length < 4) {
                                    System.out.println("Use: add_card <projectName> <cardName> <description>");
                                } else {
                                    String message = sendCmd(client, cmdLine);
                                    System.out.println(message.trim());
                                }
                                break;
                            }
                            case "move_card" : {
                                if (cmd.length != 5) {
                                    System.out.println("Use: move_card <projectName> <cardName> <startingList> <destinationList>");
                                } else {
                                    String message = sendCmd(client, cmdLine);
                                    System.out.println(message.trim());
                                }
                                break;
                            }
                            case "get_card_history" : {
                                if (cmd.length != 3) {
                                    System.out.println("Use: get_card_history <projectName> <cardName>");
                                } else {
                                    String message = sendCmd(client, cmdLine);
                                    System.out.println(message.trim());
                                }
                                break;
                            }
                            case "cancel_project" : {
                                if (cmd.length != 2) {
                                    System.out.println("Use: cancel_project <projectName>");
                                } else {
                                    String message = sendCmd(client, cmdLine);
                                    System.out.println(message.trim());
                                }
                                break;
                            }
                            case "join_chat" :{
                                if (cmd.length != 2) {
                                    System.out.println("Use: join_chat <projectName>");
                                } else {
                                    String message = sendCmd(client, cmdLine);
                                    if (!message.contains("Invalid") && !message.contains("not found") && !message.contains("denied")){
                                        try {
                                            joinChat(cmd[1], username, message.split(" "));
                                            System.out.println("Chat joined");
                                        }catch (UnknownHostException e ){
                                            System.out.println("There is an error with the multicast address");
                                            e.printStackTrace();
                                        } catch (IllegalArgumentException e){
                                            System.out.println(e.getMessage());
                                        }
                                    }
                                    else System.out.println("Can't join the chat: the project doesn't exist");
                                }
                                break;
                            }
                            case "read_msg" :{
                                if (cmd.length != 2) {
                                    System.out.println("Use: read_msg <projectName>");
                                } else {
                                    try {
                                        read_msg(cmd[1]);
                                    }catch (Exception e){
                                        System.out.println(e.getMessage());
                                    }
                                }
                                break;
                            }
                            case "send_msg" :{
                                if (cmd.length < 3) {
                                    System.out.println("Use: send_msg <projectName> <message>");
                                } else {
                                    try {
                                        send_msg(cmd[1], createMessage(cmd));
                                    }catch (Exception e){
                                        System.out.println(e.getMessage());
                                    }
                                }
                                break;
                            }
                            case "exit" : {
                                remoteRegisterManager.unregisterForCallback(stub);
                                sendCmd(client, "logout");
                                logged = false;
                                exit = true;
                                client.close();
                                break;
                            }
                            case "help":{
                                help();
                                break;
                            }
                            default :
                                System.out.println("Command not found");
                                break;
                        }
                    }
                }
            } while (!exit);

            //gestione dei possibili errori dovuti alla connessione TCP
        } catch (IOException e) {
            try {
                if (client != null)
                    client.close();
            }catch (IOException ioException) {
                ioException.printStackTrace();
            }
            System.out.println("Server unavailable");
        }

        try {
            UnicastRemoteObject.unexportObject(notifyManager, false);
        } catch (NoSuchObjectException e) {
            e.printStackTrace();
        }
    }

    //-------------------------------------------------------------------------------------//

                                        //FUNZIONE AUSILIARE

    //manda al server il comando inserito da linea di comando e riceve la relativa risposta
    private static String sendCmd(SocketChannel client, String cmdLine) throws IOException {
        //invio al server la richiesta e la sua lunghezza
        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        length.putInt(cmdLine.length());
        length.flip();
        client.write(length);
        length.clear();

        ByteBuffer buffer = ByteBuffer.wrap(cmdLine.getBytes(StandardCharsets.UTF_8));
        client.write(buffer);
        buffer.clear();

        //ricevo la risposta dal server
        ByteBuffer reply = ByteBuffer.allocate(BUFFER_DIMENSION);
        client.read(reply);
        reply.flip();

        return new String(reply.array(),StandardCharsets.UTF_8);
    }

    private static void help (){
        System.out.printf("%-68s\t%s" + System.lineSeparator(), "register <username> <password>", "register a user");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "login <username> <password>", "login");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "logout", "logout");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "list_users", "show all registered users and their state");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "list_online_users", "to show online users");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "list_projects", "show all user's project");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "create_project <projectName>", "create a project");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "add_member <projectName> <username>", "add a user to a project");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "show_members <projectName>", "show the project's members");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "show_cards <projectName>", "show all project's cards");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "show_card <projectName> <cardName>", "show a project's card");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "add_card <projectName> <cardName> <description>", "add a card to a project");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "move_card <projectName> <cardName> <startingList> <destinationList>", "move a card from a list to another one");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "get_card_history <projectName> <cardName>", "show a card's\"history\"" );
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "cancel_project <projectName>", "delete a project");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "join_chat <projectName>", "join the project's chat");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "receive <projectName>", "receive the unread messages of the project's chat");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "send_msg <projectName> <message>", "send a message to the project's chat");
        System.out.printf("%-68s\t%s"+ System.lineSeparator(), "exit", "quit");
    }

    //EFFECTS: crea una nuova chat e avvia il thread demone che si occupa della ricezione dei messaggi
    private void joinChat (String projectName, String username, String[] address) throws UnknownHostException {
        if (chats.containsKey(projectName) && !chats.get(projectName).isCancel()) throw new IllegalArgumentException("You have already joined this chat");
        else {
            Chat chat = new Chat(username, address[0], Integer.parseInt(address[1].trim()));
            chats.put(projectName, chat);
            Thread threadChat = new Thread(chat);
            threadChat.setDaemon(true);
            threadChat.start();
        }
    }

    //EFFECTS: stampa i messaggi non letti del progetto <projectName>
    private void read_msg(String projectName){
        Chat chat = chats.get(projectName);
        if (chat == null) throw new NoSuchElementException("Chat not found");
        ArrayList<String> messages = chat.readMessages();
        if (messages.isEmpty()) System.out.println("No unread message");
        else {
            for (String message : messages) {
                System.out.println(message);
            }
        }
        if (chat.isCancel()){
            chats.remove(projectName, chat);
        }
    }

    //EFFECTS: manda un messaggio multicast sulla chat del progetto <projectName>
    private void send_msg (String projectName, String message){
        Chat chat = chats.get(projectName);
        if (chat == null) throw new NoSuchElementException("Chat not found");
        if (chat.isCancel()){
            chats.remove(projectName, chat);
            throw new IllegalArgumentException("Project has been deleted");
        }
        try {
            chat.sendMessage(message);
            System.out.println("Message sent");
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
    //"ricompatta" il messaggio che l'utente vuole mandare dopo l'invocazione del metodo split()
    private static String createMessage (String[] strings){
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 2; i < strings.length; i++) {
            stringBuilder.append(strings[i]).append(" ");
        }
        return stringBuilder.toString().trim();

    }

}

