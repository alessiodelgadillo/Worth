import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RegisterManagerInterface extends Remote {


    /* REQUIRES: nickUtente != null
     * EFFECTS: registra l'utente al servizio WORTH, manda un update agli altri utenti
     *          e aggiorna il file con le informazioni di registrazione
     * THROWS:  - RemoteException se si verificano errori durante l'esecuzione della chiamata remota
     *          - ExistingNameException se il nickUtente risulta già registrato */
    void register(String nickUtente, String password) throws RemoteException, ExistingNameException;

    /* EFFECTS: registra l'utente al servizio di callback offerto da WORTH e gli manda subito un update
     *          contente la lista degli utenti registrati e il loro stato
     * THROWS:  RemoteException se si verificano errori durante l'esecuzione della chiamata remota */
    void registerForCallback (NotifyManagerInterface client) throws RemoteException;

    /* EFFECTS: rimuove la registrazione dell'utente al servizio di callback offerto da WORTH
     * THROWS:  RemoteException se si verificano errori durante l'esecuzione della chiamata remota */
    void unregisterForCallback(NotifyManagerInterface client) throws RemoteException;

}
