import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.HashMap;

public class NotifyManager extends RemoteObject implements NotifyManagerInterface {

    /* OVERVIEW: modella il gestore delle notifiche del client
     *           - users: insieme delle associazioni <username, state> */
    private final HashMap<String, String> users;

                                        //METODO COSTRUTTORE

    //THROWS:  RemoteException se si verificano errori durante l'esecuzione della chiamata remota
    public NotifyManager(HashMap<String, String> users) throws RemoteException{
        super();
        this.users=users;
    }

    //-------------------------------------------------------------------------------------//

                                        //METODO D'INTERFACCIA
    @Override
    public void notifyUpdate(HashMap<String, String> users) throws RemoteException {
        //copio le associazioni chiavi-valore dal parametro d'ingresso alla variabile d'istanza
        this.users.putAll(users);
    }
}
