import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class Chat implements Runnable{
    /* OVERVIEW: modella la chat del progetto usata dagli utenti
     *              - username: nome dell'utente
     *              - group & port: indirizzo multicast della chat
     *              - multicastSocket: socket della chat
     *              - unreadMessages: insieme dei messaggi non ancora letti dall'utente
     *              - cancel: flag che indica se il progetto è stato cancellato */

    private final String username;

    private final InetAddress group;
    private final int port;
    private MulticastSocket multicastSocket;

    private final ArrayList<String> unreadMessages;
    private final AtomicBoolean cancel;

                                        //METODO COSTRUTTORE

    public Chat(String username, String multicastAddress, int port) throws UnknownHostException {
        this.username = username;
        this.group = InetAddress.getByName(multicastAddress);
        this.port = port;
        cancel = new AtomicBoolean(false);
        unreadMessages = new ArrayList<>();
    }

    //-------------------------------------------------------------------------------------//

                                        //METODI D'ISTANZA

    //EFFECTS: restituisce true se il progetto è stato cancellato, false altrimenti
    public boolean isCancel() {

            return cancel.get();

    }

    //EFFECTS: chiude la multicastSocket
    public void close(){
        multicastSocket.close();
    }

    @Override
    // Thread demone che si occupa di ricevere i messaggi della chat
    public void run() {

        try {
            multicastSocket = new MulticastSocket(port);
            multicastSocket.joinGroup(group);
            byte[] buffer = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);

            while (!cancel.get()) {

                multicastSocket.receive(receivePacket);
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);
                if (message.equals("System: close")) {
                    cancel.set(true);
                    break;
                }
                synchronized (unreadMessages) {
                    unreadMessages.add(message);
                }
            }
            synchronized (unreadMessages) {
                unreadMessages.add("The project has been deleted");
            }

        } catch (SocketException ignored){
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //EFFECTS: restituisce i messaggi non letti
    public ArrayList<String> readMessages(){
        ArrayList<String> messages;

        synchronized (unreadMessages){
            messages = new ArrayList<>(unreadMessages);
            unreadMessages.clear();
        }

        return messages;
    }

    //EFFECTS: manda un messaggio multicast a tutti i membri del progetto
    public void sendMessage (String message) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()){
            byte[] buffer = (username +": "+ message).getBytes(StandardCharsets.UTF_8);
            DatagramPacket packetToSend = new DatagramPacket(buffer, buffer.length, group, port);
            socket.send(packetToSend);
        } catch (IOException e) {
            throw new IOException("Impossibile mandare il messaggio");
        }
    }

}
