import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;

public interface NotifyManagerInterface extends Remote {

     /* EFFECTS: aggiorna la lista degli utenti (con i relativi stati) che il client mantiene localmente
      * THROWS:  RemoteException se si verificano errori durante l'esecuzione della chiamata remota */
     void notifyUpdate (HashMap<String, String> users)throws RemoteException;
}
