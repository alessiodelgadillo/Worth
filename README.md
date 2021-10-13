# WORTH

## Introduzione

Il progetto è composto dalla directory *client* e dalla directory
*server*. All'interno della prima vi sono i file sorgente usati dal
processo omonimo, mentre nella seconda vi sono i file sorgente e le
librerie necessari all'esecuzione del processo server. In quest'ultima è
inoltre presente la directory *Recovery*, essenziale per poter
ripristinare l'ultima sessione del server. La mancanza di *Recovery*,
appropriatamente gestita, provoca la realizzazione di suddetto file e di
conseguenza la creazione di una nuova sessione. 

*Recovery* contiene il file "*users.json*", il cui scopo è memorizzare i
dati di registrazione degli utenti (username e password), e un insieme
di cartelle, una per ogni progetto. Al momento della creazione di un
progetto viene generata la corrispondente cartella la quale conterrà il
file "*members.json*" e i file delle card relative a tale progetto. Il
contenuto di tutti questi file viene aggiornato durante l'esecuzione del
programma. 

Per mantenere la persistenza dello stato del sistema al di fuori del
ciclo di vita della JVM si è scelto di usare la classe *ObjectMapper*
(inclusa nelle librerie Jackson 2.9.7 viste durante il corso) la quale
offre una gestione della serializzazione e della deserializzazione
piuttosto semplice. 

## Server

Al momento dell'avvio, se la directory *Recovery* è presente, il server
ripristina lo stato della sessione precedente andando a leggere i file
ivi contenuti, altrimenti inizializza una nuova sessione. Durante
l'esecuzione le informazioni concernenti gli utenti vengono memorizzate
all'interno di una `List<User>` mentre quelle riguardanti i progetti
vengono salvate in una `LinkedList<Projects>`. Si è deciso di
utilizzare tali collezioni per trovare un compromesso tra prestazioni e
uso della memoria. 

Si noti che per motivi di *information hiding* e *separation of
concerns* è stato scelto di usare due classi per "rappresentare" il
server:

-   *ServerWORTH*: contiene tutti i metodi e le collezioni fondamentali
    per l'esecuzione del server della piattaforma;

-   *ServerMain*: contiene la funzione *main* e al suo interno istanzia
    un oggetto di tipo *ServerWORTH* invocandone successivamente i
    metodi.

### Remote Method Invocation

Una volta caricato lo stato del server viene creata un'istanza della
classe *RegisterManager* che viene esportata e registrata sulla porta
4567 in modo tale che i processi client possano invocarne i metodi per
registrarsi a WORkTogetHer (WORTH) e registrarsi/deregistrarsi al
servizio di callback. 

#### Registrazione a WORTH

Dopo che il server ha creato l'oggetto remoto e pubblicato il suo
riferimento, il client può invocarne i metodi. Tra questi vi è il metodo
*register* che prende in input lo username e la password scelti
dall'utente e, se il nome utente non è già usato, crea un nuovo oggetto
*User* aggiungendolo alla sua struttura dati. 

Si noti che la `List<User> utenti` è la stessa collezione usata
dalla classe *ServerWORTH* passata come riferimento al costruttore di
*RegisterManager* al fine di avere modifiche consistenti. Nel caso in
cui lo username sia già usato, verrà lanciata un'eccezione
*ExistingNameException*. 

In seguito all'aggiunta dell'utente alla collezione, il metodo invierà
una notifica a tutti i client collegati aggiornando la loro lista di
utenti con i loro rispettivi stati e aggiornerà il file "*users.json*"
in *Recovery*. 

#### Callback

Gli altri metodi offerti da *RegisterManager* sono la
*registerForCallback* e la *unregisterForCallback*, i quali permettono
rispettivamente di registrarsi al servizio di callback (usato per
ricevere notifiche asincrone sugli eventi di registrazione, login e
logout degli altri utenti) e di deregistrarsi da tale servizio. Per
poter fornire questa funzionalità, *RegisterManager* mantiene al suo
interno una `LinkedList<NotifyManagerInterface>` che contiene le
*NotifyManagerInterface* passate dai client come parametro al momento
della chiamata di *registerForCallback*. 

Ogni volta che avviene una registrazione, un login oppure un logout di
un utente, il server esegue il metodo *update* che scorre la LinkedList
e manda la lista aggiornata degli utenti registrati ai vari client. 

È bene notare che la lista degli utenti che viene mandata ai client è in
realtà una HashMap che utilizza come chiave il nome dell'utente e come
valore il suo stato; la conversione da `List<User>` a
`HashMap<String, String>` è stata implementata al fine di
condividere solo le informazioni strettamente necessarie. 

### Connessione TCP

A questo punto il server si mette in ascolto sulla porta 5678 per
ricevere eventuali richieste di connessione TCP provenienti dai
client. Per eseguire le richieste dei client il server effettua il
multiplexing dei canali mediante NIO, in questo modo è possibile avere
un unico thread che gestisce più connessioni di rete, consentendo così
di non avere overhead causato dal thread switching e ottenere un
miglioramento delle performance e della scalabilità rispetto all'uso di
un server multithreaded. 

Ogniqualvolta il server riceve un comando da un processo client
controlla quale operazione l'utente sta richiedendo ed esegue il metodo
appropriato. Le operazioni inerenti a un singolo progetto, o a una/più
card, vengono eseguite attraverso le funzioni messe a disposizione dalle
classi *Project* e *Card*, che modellano rispettivamente un singolo
progetto e una singola card. All'interno di ogni metodo vengono svolti
gli opportuni controlli di correttezza: ogni errore dovuto all'utente
viene fatto "galleggiare" tramite l'uso delle eccezioni; il client
riceverà l'adeguato messaggio di errore. 

Inoltre è bene chiarire che quando un utente esegue il login il suo
indirizzo IP e il suo numero di porta vengono salvati: l'obiettivo è
identificare univocamente tutti gli utenti connessi al servizio al di là
dello username usato in WORTH. Questo approccio semplifica il controllo
dei diritti di accesso eseguiti sull'utente stesso al momento della
richiesta di operazioni su un determinato progetto.

### UDP Multicast

Nella classe *Project*, insieme agli `ArrayList<Card>`
rappresentanti le liste che definiscono il flusso di lavoro e
all'`ArrayList<String>` in cui vengono salvati i nomi dei membri,
viene memorizzato anche l'indirizzo multicast relativo alla chat del
progetto. Questo indirizzo viene usato per le notifiche di eventi legati
allo spostamento di una card da una lista all'altra e per informare i
membri della cancellazione del progetto tramite la stringa
"*System: close*". 

L'assegnazione del multicast address nell'istante in cui un progetto
viene creato può avvenire in due modi:

-   viene generato automaticamente a partire dall'ultimo indirizzo usato
    (memorizzato nella variabile *multicastIP* contenuta nella classe
    *ServerWORTH*);

-   viene ottenuto dalla `LinkedList<String> oldAddress` che
    contiene gli indirizzi dei progetti eliminati in precedenza.

![Relazione tra le classi dell'applicazione
server](img/Server.png)

## Client

Analogamente al server, anche il client è stato "rappresentato" mediante
l'uso di due classi:

-   *ClientWORTH*: contiene tutti i metodi e le collezioni necessarie
    per la corretta esecuzione;

-   *ClientMain*: contiene la funzione *main*, all'interno della quale
    istanzia un oggetto di tipo *ClientWORTH* e ne invoca i metodi.

Nel corso della sua esecuzione il client usa una `HashMap<String,
String>` e una `HashMap<String, Chat>` che servono per mantenere
rispettivamente le associazioni \<nome utente, stato\> e \<nome
progetto, chat\>. In questo caso sono state scelte tali collezioni per
avere una ricerca efficiente in entrambe le strutture.

### Gestione delle richieste

All'inizio della sua esecuzione, mediante il metodo *registerToService*,
il client recupera l'oggetto remoto esportato dal server sulla porta
4567 ed esporta un'istanza di *NotifyManager* per ricevere le callback
dal processo server. Dopodiché finché non ha eseguito il login, l'utente
può usare solo i comandi register, login, help ed exit. Il primo
comando, anticipato già in precedenza, viene eseguito tramite
l'invocazione del metodo dell'oggetto remoto messo a disposizione dal
server. Una volta eseguito il login, l'utente si registra per il
servizio di callback e ottiene la possibilità di eseguire tutti gli
altri comandi (ad eccezione di register e login, ovviamente).  

Si osservi che i comandi list\_users e list\_online\_users non
richiedano la connessione TCP poiché lavorano sulla struttura dati
locale, tuttavia per poter "riempirla" è necessario essere registrati al
servizio di callback dopo previo login. 

In seguito al comando di logout, la connessione TCP viene chiusa: tale
scelta è coerente con l'idea di avere una connessione per login e quindi
avere un indirizzo IP e una porta che identificano univocamente un
utente. 

L'applicazione client esegue tutti i controlli di correttezza sui
comandi, quindi anche sul numero di argomenti, prima di mandarli
all'applicazione server cosicché da evitare traffico di rete inutile. 

### Chat

Con il comando join\_chat l'utente ha la possibilità di partecipare alla
chat di un progetto. Tale comando viene mandato sulla connessione TCP al
server che risponderà con l'indirizzo multicast e la porta appartenenti
al progetto; questi parametri verrano usati all'interno del costruttore
della classe *Chat* andando a istanziare una *MulticastSocket*.

La classe *Chat* offre i metodi di *readMessages*, per leggere i
messaggi non letti, e *sendMessage*, per mandare un messaggio. Per poter
implementare il primo metodo è stato usato un `ArrayList<String>`
che viene aggiornato da un thread demone che sta in attesa di ricevere i
messaggi e al momento della ricezione li aggiunge a tale struttura. Dopo
che l'utente ha letto tutti i messaggi la collezione viene svuotata.

Quando riceve il messaggio "*System: close*", il thread demone imposta
l'*AtomicBoolean cancel* a *true* (per indicare che il progetto è stato
chiuso), aggiunge la stringa "*The project has been deleted*" ai
messaggi non letti e termina la sua esecuzione. Qualora il client andrà
a leggere i messaggi o a mandarne uno, farà un controllo su *cancel* ed
eliminerà la chat dalla sua HashMap. 

Nel caso in cui l'utente esegua il logout, viene invocato il metodo
*close*, sempre della classe *Chat*, che chiude la *MulticastSocket* e
di conseguenza termina il thread demone. Fatto ciò, la struttura dati
delle chat viene completamente svuotata per evitare un possibile accesso
dall'utente che accederà successivamente.

![Relazione tra le classi dell'applicazione
client](img/Client.png)

## Note finali

1.  Tutti i metodi remoti di *registerManager* sono *synchronized* per
    evitare che possano essere eseguiti contemporaneamente da più
    client.

2.  *registerForCallback* e *unregisterForCallback* contengono un blocco
    *synchronized* per evitare inconsistenze nella collezione di 
    *NotifyManagerInterface*.

3.  La `List<User>` condivisa tra *RegisterManager* e *ServerWORTH*
    è una *synchronized List* per evitare modifiche inconsistenti.

4.  Sono state utilizzate due enumerazioni, *UserState* e *CardState*,
    per rappresentare gli stati degli utenti e delle card.

5.  All'interno di tutto il progetto si è cercato di fare uso delle
    eccezioni fornite direttamente da Java, eccezion fatta per
    *ExistingNameException* e *WrongPswException*.

6.  Sono state gestite, seppur in modo semplice, condizioni di
    terminazione anomale come la chiusura improvvisa del server e la
    chiusura improvvisa di un client.

## Compilazione ed esecuzione

Il progetto è stato testato sia su Windows 10 v.20H2 che su Ubuntu 20.04
usando Java SE 11 e superiore, in particolar modo sono stati evitati
costrutti come l'enhanced switch (introdotto dalla versione 13) per
favorirne la portabilità. Si noti che, se i file sorgente del client
vengono compilati con una versione uguale o superiore alla 14, appare il
messaggio:

```bash 
Note: src\Chat.java uses or overrides a deprecated API.
```

questo perché, a partire dalla Java SE 14, il metodo *joinGroup* della
classe *MulticastSocket* risulta deprecato. 

### Windows

#### Server

Per compilare i file sorgente e le librerie del server è necessario
posizionarsi all'interno della directory *server* ed eseguire il
comando:

``` bash
javac -cp .\lib\jackson-annotations-2.9.7.jar;.\lib\jackson-core-2.9.7.jar;.\lib\jackson-databind-2.9.7.jar; .\src\*.java -d bin
```
A questo punto, dalla stessa directory, è possibile avviare il processo
eseguendo il seguente comando:

```bash
java -cp .\lib\jackson-annotations-2.9.7.jar;.\lib\jackson-core-2.9.7.jar;.\lib\jackson-databind-2.9.7.jar;bin; ServerMain
```

#### Client

Per compilare i file sorgente del client è necessario posizionarsi
all'interno della directory *client* ed eseguire il comando:

```bash
javac .\src\*.java -d bin
```
A questo punto, dalla stessa directory, è possibile avviare il processo
eseguendo il seguente comando:

```bash
java -cp bin; ClientMain
```

### Linux

#### Server

Per compilare i file sorgente e le librerie del server è necessario
posizionarsi all'interno della directory *server* ed eseguire il
comando:

``` bash
javac -cp ./lib/jackson-annotations-2.9.7.jar:./lib/jackson-core-2.9.7.jar:./lib/jackson-databind-2.9.7.jar: ./src/*.java -d bin
```
A questo punto, dalla stessa directory, è possibile avviare il processo
eseguendo il seguente comando:

``` bash
java -cp ./lib/jackson-annotations-2.9.7.jar:./lib/jackson-core-2.9.7.jar:./lib/jackson-databind-2.9.7.jar:bin: ServerMain
```

#### Client

Per compilare i file sorgente del client è necessario posizionarsi
all'interno della directory *client* ed eseguire il comando:

```bash
javac ./src/*.java -d bin
```

A questo punto, dalla stessa directory, è possibile avviare il processo
eseguendo il seguente comando:

```bash
java -cp bin: ClientMain
```

### Comandi

La seguente tabella fornisce un riassunto dei comandi offerti
dall'applicazione WORTH. Per ulteriori dettagli è possibile usare il
comando help da terminale.

| *Comando*           | *Descrizione*                                      |
|---------------------|----------------------------------------------------|
| `help`              | mostra tutti i comandi con i relativi parametri    |
| `register`          | registra un nuovo utente                           |
| `login`             | esegue l'accesso                                   |
| `logout`            | esce dall'account                                  |
| `list_users`        | mostra tutti gli utenti registrati e il loro stato |
| `list_online_users` | mostra tutti gli utenti online                     |
| `list_projects`     | mostra tutti i progetti di cui l'utente è membro   |
| `create_project`    | crea un nuovo progetto                             |
| `add_member`        | aggiunge un nuovo membro a un progetto             |
| `show_members`      | mostra tutti i membri di un progetto               |
| `show_cards`        | mostra tutte le card presenti in un progetto       |
| `show_card`         | mostra le informazioni di una card                 |
| `add_card`          | aggiunge una nuova card a un progetto              |
| `move_card`         | sposta una card da una lista a un'altra            |
| `get_card_history`  | mostra la "storia" di una card                     |
| `cancel_project`    | elimina un progetto                                |
| `read_msg`          | mostra tutti i messaggi non letti di una chat      |
| `send_msg`          | manda un messaggio sulla chat del gruppo           |
| `exit`              | termina il programma                               |
