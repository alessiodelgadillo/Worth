import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class RegisterManager extends RemoteServer implements RegisterManagerInterface {

    /* OVERVIEW: modella il gestore delle registrazioni per il servizio WORTH
    *               - utenti: lista degli utenti registrati al servizio
    *               - clients: insieme delle interfacce degli utenti per eseguire le callback */

    private final List<User> utenti;
    private final LinkedList<NotifyManagerInterface> clients;

                                        //METODO COSTRUTTORE

    //THROWS:  RemoteException se si verificano errori durante l'esecuzione della chiamata remota
    public RegisterManager(List<User> utenti) throws RemoteException {
        this.utenti = utenti;
        this.clients = new LinkedList<>();
    }

    //-------------------------------------------------------------------------------------//

                                        //METODI D'INTERFACCIA
    @Override
    public synchronized void register(String nickUtente, String password) throws RemoteException, ExistingNameException {
        if (nickUtente == null) throw new NullPointerException("Invalid username");
        for (User utente: utenti)
            if (utente.getName().equals(nickUtente)) throw new ExistingNameException("Username already exists");
        this.utenti.add(new User(nickUtente, password, UserState.OFFLINE));
        update(listToMap(utenti));
        saveRegister(utenti);
    }

    @Override
    public synchronized void registerForCallback(NotifyManagerInterface client) throws RemoteException {
        synchronized (clients) {
            if (!clients.contains(client)) {
                clients.add(client);
                System.out.println("New client registered");
                client.notifyUpdate(listToMap(utenti));
            }
        }
    }

    @Override
    public synchronized void unregisterForCallback(NotifyManagerInterface client) throws RemoteException{
        synchronized (clients) {
            if (clients.remove(client)) System.out.println("Client unregistered");
            else System.out.println("Unable to unregister client");
        }
    }

    //-------------------------------------------------------------------------------------//

                                        //METODI DI ISTANZA

    /* EFFECTS: esegue una callback per ogni utente mandando un HashMap
                contenente l'associazione <username, stato>
     * THROWS:  RemoteException se si verificano errori durante l'esecuzione della chiamata remota */
    public void update (HashMap<String, String> users) throws RemoteException {
        doCallbacks(users);
        System.out.println("Update complete.");
    }

    private void doCallbacks (HashMap<String, String> users) {

        for (int i = 0; i < clients.size(); i++) {
            NotifyManagerInterface anInterface = clients.get(i);
            try {
                anInterface.notifyUpdate(users);
            } catch (RemoteException remoteException) {
                clients.remove(anInterface);
            }
        }
    }

    //-------------------------------------------------------------------------------------//

                                        //FUNZIONI AUSILIARIE

    /* EFFECTS: trasforma un ArrayList<User> in un'HashMap<String, String>
     *          usata per condividere solo le informazioni essenziali
     * RETURN: insieme delle associazioni <username, stato>*/
    private static HashMap<String, String> listToMap (List<User> users){
        HashMap<String, String> usersMap = new HashMap<>();
        for (User user: users) {
            usersMap.put(user.getName(), user.getState().toString());
        }
        return usersMap;
    }

    // EFFECTS: aggiorna il file users.json usato per mantenere le informazioni di registrazione
    private static void saveRegister (List<User> users){
        File dir = new File("./Recovery");
        if (!dir.exists()) {
            if (dir.mkdir()) {
                System.out.println("Directory di recovery creata");
            }
        }
        File usersFile = new File(dir.getAbsolutePath() + File.separator + "users.json");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            objectMapper.writeValue(usersFile, users);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
