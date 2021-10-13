import com.fasterxml.jackson.annotation.JsonIgnore;

import java.net.InetAddress;

public class User {
    /* OVERVIEW: modella l'utente che usa il servizio WORTH
                    - name: username dell'utente
                    - password: password dell'utente
                    - state: stato dell'utente -> {ONLINE, OFFLINE}
                    - address: indirizzo IP dell'host sul quale l'utente ha eseguito il login
                    - port: numero di porta dell'host sul quale l'utente ha eseguito il login */

    private String name;
    private String password;

    @JsonIgnore
    private UserState state;
    @JsonIgnore
    private InetAddress address;
    @JsonIgnore
    private int port;

                                        //METODI COSTRUTTORE

    public User(String name, String password, UserState state) {
        if (name == null) throw new NullPointerException("Invalid Username");
        if (password == null) throw new NullPointerException("Invalid Password");
        this.name = name;
        this.password = password;
        this.state = state;
    }

    public User(){}

    //-------------------------------------------------------------------------------------//

                                        //METODI GETTER/SETTER

    public String getName() { return name; }

    public String getPassword() { return password; }

    public UserState getState() { return state; }

    public InetAddress getAddress() { return address; }

    public int getPort() { return port; }

    public void setState(UserState state) { this.state = state; }

    public void setAddress(InetAddress address) { this.address = address; }

    public void setPort(int port) { this.port = port; }

}
