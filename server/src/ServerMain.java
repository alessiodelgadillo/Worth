public class ServerMain {

    public static void main (String[] args){

        ServerWORTH server = new ServerWORTH();
        server.registerService();
        server.start();
    }
}
