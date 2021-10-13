import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public class ClientMain {
    public static void main (String[] args) {

        ClientWORTH client = new ClientWORTH();

        try {
            client.registerToService();
        } catch (RemoteException | NotBoundException remoteException) {
            remoteException.printStackTrace();
            return;
        }

        client.start();
    }
}