import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.awt.desktop.SystemSleepEvent;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;

public class Project {

    /* OVERVIEW: modella i progetti WORTH
     *              - name: nome del progetto
     *              - members: insieme dei nomi dei membri del progetto
     *              - group & port: indirizzo di multicast della chat del progetto */

    private String name;
    private ArrayList<String> members;

    //liste che definiscono il flusso di lavoro
    private ArrayList<Card> toDo;
    private ArrayList<Card> inProgress;
    private ArrayList<Card> toBeRevised;
    private ArrayList<Card> done;

    private InetAddress group;
    private int port;

                                        //METODI COSTRUTTORE

    public Project(String name, String creator, String multicastAddress) {

        if (name == null) throw new NullPointerException("Invalid project name");
        if (creator == null) throw new NullPointerException("Invalid creator");
        this.name = name;
        this.members = new ArrayList<>();
        this.members.add(creator);
        this.toDo= new ArrayList<>();
        this.inProgress = new ArrayList<>();
        this.toBeRevised = new ArrayList<>();
        this.done = new ArrayList<>();

        try {
            group = InetAddress.getByName(multicastAddress);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        port = 4000;
        saveMembers(this);
    }

    public Project(String name, String multicastAddress) {

        if (name == null) throw new NullPointerException("Invalid project name");
        this.name = name;
        this.members = new ArrayList<>();
        this.toDo= new ArrayList<>();
        this.inProgress = new ArrayList<>();
        this.toBeRevised = new ArrayList<>();
        this.done = new ArrayList<>();

        try {
            group = InetAddress.getByName(multicastAddress);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        port = 4000;
    }

    //-------------------------------------------------------------------------------------//

                                        //METODI GETTER/SETTER

    public String getName() { return name; }

    public ArrayList<String> getMembers() { return members; }

    public ArrayList<Card> getToDo() { return toDo; }

    public ArrayList<Card> getInProgress() { return inProgress; }

    public ArrayList<Card> getToBeRevised() { return toBeRevised; }

    public ArrayList<Card> getDone() { return done; }

    public InetAddress getGroup() {
        return group;
    }

    public int getPort() {
        return port;
    }

    public void setMembers(ArrayList<String> members) { this.members = members; }

    //-------------------------------------------------------------------------------------//

                                        //METODI D'ISTANZA


    /* REQUIRES: user != null
     * EFFECTS: controlla che user sia membro di un progetto
     * THROWS: NullPointerException se user == null
     * RETURN: true se user appartiene members, false altrimenti */
    public boolean isMember (String user){
        if (user == null) throw new NullPointerException("Invalid user name");
        for (String member: members) {
            if (member.equals(user)){
                return true;
            }
        }
        return false;
    }

    /* REQUIRES: member != null
     * EFFECTS: aggiunge member alla lista dei membri e salva le modifiche su members.json
     * THROWS: - NullPointerException se member == null
     *         - ExistingNameException se member è già presente nella lista dei membri */
    public void addMember (String member) throws ExistingNameException {
        if (member == null) throw new NullPointerException("Invalid user name");
        if (this.members.contains(member)) throw new ExistingNameException("Member already added");
        this.members.add(member);
        saveMembers(this);
    }

    /* EFFECTS: crea una stringa contente la lista dei membri del progetto
     * RETURN: la lista dei membri del progetto */
    public String showMembers (){
        StringBuilder list = new StringBuilder();
        for (String member: members) {
            list.append(member).append(" ");
        }
        return list.toString();
    }

    /* EFFECTS: crea una stringa contente l'insieme di tutte le carte del progetto
     * RETURN: la lista di tutte le stringhe del progetto */
    public String showCards (){
        StringBuilder list = new StringBuilder();

        list.append("TO DO: ");
        appendList(list, toDo);

        list.append("IN PROGRESS: ");
        appendList(list, inProgress);

        list.append("TO BE REVISED: ");
        appendList(list, toBeRevised);

        list.append("DONE: ");
        appendList(list, done);

        return list.toString();
    }

    /* REQUIRES: cardName != null
     * EFFECTS: cerca la card per nome e ne restituisce le informazioni
     * THROWS: - NullPointerException se cardName == null
     *         - NoSuchElementException se la card non viene trovata
     * RETURN: nome, descrizione e lista della card*/
    public String showCard (String cardName) {
        if (cardName == null) throw new NullPointerException("Invalid card name");
        Card card = getCard(cardName, this::searchCard);
        if (card == null) throw new NoSuchElementException("Card not found");
        return card.getInformation();
    }

    /* REQUIRES: cardName != null
     * EFFECTS: aggiunge la card "cardName" con descrizione "description" alla lista toDo
     * THROWS: - NullPointerException se cardName == null
     *         - ExistingNameException se la card è già presente
     * RETURN: */
    public void addCard (String cardName, String description) throws ExistingNameException {
        if (cardName == null) throw new NullPointerException("Invalid card name");
        if (getCard(cardName, this::searchCard) != null) throw new ExistingNameException("Card already exists");
        Card card = new Card(cardName, description);
        toDo.add(card);
        saveCard(this.name, card );
    }

    /* REQUIRES: cardName != null && listaPartenza != null && listaDestinazione != null
     * EFFECTS: sposta la card da "listaPartenza" a "listaDestinazione"
     * THROWS: - NullPointerException se cardName == nulla || listaPartenza == null || listaDestinazione == null
     *         - IllegalArgumentException se "listaPartenza" e "listaDestinazione" non rispettano i vincoli della specifica */
    public void moveCard (String cardName, String listaPartenza,  String listaDestinazione){
        if (cardName == null) throw new NullPointerException("Invalid card name");
        if (listaPartenza == null) throw new NullPointerException("Invalid starting list");
        if (listaDestinazione == null) throw new NullPointerException("Invalid destination list");

        if (listaPartenza.equals(listaDestinazione))
            throw new IllegalArgumentException("The starting list and the destination list are the same list");
        if (listaPartenza.equals("done"))
            throw new IllegalArgumentException("You cannot move a card from the done list");
        if (listaDestinazione.equals("todo"))
            throw new IllegalArgumentException("You cannot move a card to the to do list");
        if (listaPartenza.equals("todo") && !listaDestinazione.equals("inprogress"))
            throw new IllegalArgumentException("A card in the to do list can be move only to the in progress list");

        if (!listaDestinazione.equals("inprogress") && !listaDestinazione.equals("toberevised") && !listaDestinazione.equals("done"))
            throw new IllegalArgumentException("The destination list doesn't exist");


        Card card = null;
        switch (listaPartenza) {
            case "todo" : card = removeCard(cardName, toDo); break;
            case "inprogress" : card = removeCard(cardName, inProgress); break;
            case "toberevised" : card = removeCard(cardName, toBeRevised); break;
        }
        if (card == null) throw new NoSuchElementException("Card not found");
        switch (listaDestinazione) {
            case "inprogress" : {
                card.addToStory(CardState.InProgress);
                inProgress.add(card);
                break;
            }
            case "toberevised" : {
                card.addToStory(CardState.ToBeRevised);
                toBeRevised.add(card);
                break;
            }
            case "done" : {
                card.addToStory(CardState.Done);
                done.add(card);
                break;
            }
        }
        sendMessage("Card "+ cardName +" moved from " + listaPartenza + " to " + listaDestinazione);
        saveCard(this.name, card );
    }

    /* REQUIRES: cardName != null
     * EFFECTS: cerca la card per nome e ne restituisce la "storia"
     * THROWS: - NullPointerException se cardName == null
     *         - NoSuchElementException se la card non viene trovata
     * RETURN: la "storia della card" */
    public String getCardHistory (String cardName){
        if (cardName == null) throw new NullPointerException("Invalid card name");
        Card card = getCard(cardName, this::searchCard);
        if (card == null) throw new NoSuchElementException("Card not found");
        return card.getHistory();
    }

    /* EFFECTS: controlla se il progetto è terminato
     * RETURN: true se il progetto è terminato, false altrimenti*/
    public boolean isDone (){
        return toDo.size() == 0 && inProgress.size() == 0 && toBeRevised.size() == 0;
    }

    /* EFFECTS: manda un messaggio in multicast a tutti i membri del progetto */
    public void sendMessage (String message){
        try (DatagramSocket socket = new DatagramSocket()){
            byte[] buffer = ("System: "+ message).getBytes(StandardCharsets.UTF_8);
            DatagramPacket packetToSend = new DatagramPacket(buffer, buffer.length, group, port);
            socket.send(packetToSend);

        } catch (IOException e) {
            System.out.println("Impossibile mandare il messaggio");
        }
    }

    //-------------------------------------------------------------------------------------//

                                        //FUNZIONI AUSILIARIE

    //funzione di ordine superiore che cerca una card in tutte le liste e se la trova la restituisce
    private Card getCard (String cardName, BiFunction<String, ArrayList<Card>, Card> function){
        Card tmp;

        if ((tmp = function.apply(cardName, toDo) ) != null) return tmp;
        if ((tmp = function.apply(cardName, inProgress) ) != null) return tmp;
        if ((tmp = function.apply(cardName, toBeRevised) ) != null) return tmp;
        if ((tmp = function.apply(cardName, done) ) != null) return tmp;

        return null;
    }

    //funzione che rimuove una card da listCard e la restituisce
    private Card removeCard (String cardName, ArrayList<Card> listCard){
        for (int i=0; i< listCard.size(); i++){
            Card tmp = listCard.get(i);
            if (tmp.getName().equals(cardName)){
                listCard.remove(i);
                return tmp;
            }
        }
        return null;
    }

    //funzione che cerca una card in una lista specifica e la restituisce (senza rimuoverla)
    private Card searchCard (String cardName, ArrayList<Card> listCard) {
        for (Card tmp : listCard) {
            if (tmp.getName().equals(cardName)) {
                return tmp;
            }
        }
        return null;
    }

    //funzione che crea una stringa che rappresenta una delle liste del progetto e la restituisce
    private static StringBuilder appendList (StringBuilder list, ArrayList<Card> cardArrayList){
        for (Card card: cardArrayList) {
            list.append(card.getName()).append(" ");
        }
        list.append(System.lineSeparator());
        return list;
    }

    /* funzione che crea la directory di un progetto se non esiste
       e salva i nomi dei membri del progetto su members.json */
    private static void saveMembers (Project project){
        File projectDir = new File("./Recovery" + File.separator + project.getName());

        if (!projectDir.exists()){
            if (projectDir.mkdir()) System.out.println("Project directory created");
        }
        File membersFile = new File(projectDir.getAbsolutePath() + File.separator + "members.json");
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            mapper.writeValue(membersFile, project.getMembers());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //funzione che salva le card di un progetto nella relativa directory
    private static void saveCard (String projectName, Card card){
        File cardFile = new File("./Recovery" + File.separator + projectName + File.separator + card.getName()+".json");
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            mapper.writeValue(cardFile, card);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
