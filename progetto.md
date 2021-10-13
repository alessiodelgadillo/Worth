# Progetto di Laboratorio di Reti

## Descrizione del problema

Negli ultimi anni sono state create numerose applicazioni collaborative, per la condivisione di contenuti, messaggistica, videoconferenza, gestione di progetti, ecc.
In questo progetto didattico, WORTH (WORkTogetHer), ci focalizzeremo sull'organizzazione e la gestione di progetti in modo collaborativo.
Le applicazioni di collaborazione e project management (es. Trello, Asana) aiutano le persone a organizzarsi e coordinarsi nello svolgimento di progetti comuni.
Questi possono essere progetti professionali, o in generale qualsiasi attività possa essere organizzata in una serie di compiti (es. to do list) che sono svolti da
membri di un gruppo: le applicazioni di interesse sono di diverso tipo, si pensi alla organizzazione di un progetto di sviluppo software con i colleghi del team di sviluppo, ma anche all'organizzazione di una festa con un gruppo di amici.

Alcuni di questi tool (es. Trello) implementano il metodo Kanban (cartello o cartellone pubblicitario, in giapponese), un metodo di gestione "agile".
La lavagna Kanban fornisce una vista di insieme delle attività e ne visualizza l'evoluzione, ad esempio dalla creazione e il successivo progresso fino al completamento, dopo che è stata superata con successo la fase di revisione.
Una persona del gruppo di lavoro può prendere in carico un'attività quando ne ha la possibilità, spostando l'attività sulla lavagna.

Il progetto consiste nell'implementazione di **WORkTogetHer** (**WORTH**): uno strumento per la gestione di progetti collaborativi che si ispira ad alcuni principi della metodologia Kanban.

## Specifica delle operazioni

Gli utenti possono accedere a WORTH dopo registrazione e login.

In WORTH, un progetto, identificato da un nome univoco, è costituito da una serie di "card" ("carte"), che rappresentano i compiti da svolgere per portarlo a termine, e fornisce una serie di servizi.
Ad ogni progetto è associata una lista di membri, ovvero utenti che hanno i permessi per modificare le card e accedere ai servizi associati al progetto (es. chat).

Una card è composta da un nome e una descrizione testuale.
Il nome assegnato alla card deve essere univoco nell'ambito di un progetto.
Ogni progetto ha associate quattro liste che definiscono il flusso di lavoro come passaggio delle card da una lista alla successiva: TODO, INPROGRESS, TOBEREVISED, DONE.
Qualsiasi membro del progetto può spostare la card da una lista all'altro, rispettando i vincoli illustrati nel diagramma in basso.

Le card appena create sono automaticamente inserite nella lista TODO.
Qualsiasi membro può spostare una card da una lista all'altra.
Quando tutte le card sono nella lista DONE il progetto può essere cancellato, da un qualsiasi membro partecipante al progetto.

Ad ogni progetto è associata una chat di gruppo, e tutti i membri di quel progetto, se online (dopo aver effettuato il login), possono ricevere e inviare i messaggi sulla chat.
Sulla chat il sistema invia inoltre automaticamente le notifiche di eventi legati allo spostamento di una card del progetto da una lista all'altra.

![](img/diagramma.png)

Un utente registrato e dopo login eseguita con successo ha i permessi per:

- recuperare la lista di tutti gli utenti registrati al servizio;
- recuperare la lista di tutti gli utenti registrati al servizio e collegati al servizio (in stato online);
- creare un progetto;
- recuperare la lista dei progetti di cui è membro.

Un utente che ha creato un progetto ne diventa automaticamente membro.
Può aggiungere altri utenti registrati come membri del progetto.
Tutti i membri del progetto hanno gli stessi diritti (il creatore stesso è
un membro come gli altri), in particolare:

- aggiungere altri utenti registrati come membri del progetto;
- recuperare la lista dei membri del progetto;
- creare card nel progetto;
- recuperare la lista di card associate ad un progetto;
- recuperare le informazioni di una specifica card del progetto;
- recuperare la "storia" di una specifica card del progetto (vedi seguito per dettagli);
- spostare qualsiasi card del progetto (rispettando i vincoli del diagramma);
- inviare un messaggio sulla chat di progetto;
- leggere messaggi dalla chat di gruppo;
- cancellare il progetto.

Di seguito sono specificate le operazioni offerte dal servizio.
In sede di implementazione è possibile aggiungere ulteriori parametri, se necessario.

`register(nickUtente, password)`: per inserire un nuovo utente, il server mette a disposizione una operazione di registrazione di un utente.
Il server risponde con un codice che può indicare l'avvenuta registrazione, oppure, se il nickname è già presente, o se la password è vuota, restituisce un messaggio d'errore.
Come specificato in seguito, le registrazioni sono tra le informazioni da persistere.

`login(nickUtente, password)`: login di un utente già registrato per accedere al servizio.
Il server risponde con un codice che può indicare l'avvenuto login, oppure, se l'utente ha già effettuato la login o la password è errata, restituisce un messaggio d'errore.

`logout(nickUtente)`: effettua il logout dell'utente dal servizio.

`listUsers()`: utilizzata da un utente per visualizzare la lista dei nickUtente registrati al servizio e il loro stato (online o offline).

`listOnlineusers()`: utilizzata da un utente per visualizzare la lista dei nickUtente registrati al servizio e online in quel momento.

`listProjects()`: operazione per recuperare la lista dei progetti di cui l'utente è membro.

`createProject(projectName)`: operazione per richiedere la creazione di un nuovo progetto.
Se l'operazione va a buon fine, il progetto è creato e ha come membro l'utente che ne ha richiesto la creazione.

`addMember(projectName, nickUtente)`: operazione per aggiungere l'utente `nickUtente` al progetto `projectname`.
Se l'utente è registrato l'aggiunta come membro è eseguita senza chiedere il consenso a nickUtente, se l'utente non è registrato l'operazione non può essere completata e il servizio restituisce un messaggio di errore.

`showMembers(projectName)`: operazione per recuperare la lista dei membri del progetto.

`showCards(projectName)`: operazione per recuperare la lista di card associate ad un progetto `projectName`.

`showCard(projectName, cardName)`: operazione per recuperare le informazioni (nome, descrizione testuale, lista in cui si trova in quel momento) della card `cardName` associata ad un progetto `projectName`.

`addCard(projectName, cardName, descrizione)`: operazione per richiedere l'aggiunta della card di nome `cardName` al progetto `projectname`.
La card deve essere accompagnata da una breve testo descrittivo.
La card viene automaticamente inserita nella lista TODO.

`moveCard(projectName, cardName, listaPartenza, listaDestinazione)`: operazione per richiedere lo spostamento della card di nome `cardName` al progetto `projectname` dalla lista `listaPartenza` alla lista `listaDestinazione`.

`getCardHistory(projectName, cardName)`: operazione per richiedere la "storia" della card, ovvero la sequenza di eventi di spostamento della card, dalla creazione allo spostamento più recente.

`readChat(projectName)`: operazione per visualizzare i messaggi della chat del progetto `projectName`.

`sendChatMsg(projectName, messaggio)`: l'utente invia un messaggio alla chat del progetto `projectName`.

`cancelProject(projectName)`: un membro di progetto chiede di cancellare un progetto.
L'operazione può essere completata con successo solo se tutte le card sono nella lista DONE.

|      Permessi     | Utente | Membro di progetto |
|:------------------|:------:|:------------------:|
| `listUsers`       |    X   |                    |
| `listOnlineusers` |    X   |                    |
| `listProjects`    |    X   |                    |
| `createProject`   |    X   |                    |
| `addMember`       |        |          X         |
| `showMembers`     |        |          X         |
| `showCards`       |        |          X         |
| `showCard`        |        |          X         |
| `addCard`         |        |          X         |
| `moveCard`        |        |          X         |
| `getCardHistory`  |        |          X         |
| `readChat`        |        |          X         |
| `sendChatMsg`     |        |          X         |
| `cancelProject`   |        |          X         |

## Specifiche per l'implementazioni

Nella realizzazione del progetto devono essere utilizzate molte delle tecnologie illustrate durante il corso.
In particolare:

- La fase di registrazione viene implementata mediante RMI.
- La fase di login deve essere effettuata come prima operazione dopo aver instaurato una connessione TCP con il server.
In risposta all’operazione di login, il server invia anche la lista degli utenti registrati e il loro stato (online, offline)[^1].
A seguito della login il client si registra ad un servizio di notifica del server per ricevere aggiornamenti sullo stato degli utenti registrati (online/offline).
Il servizio di notifica deve essere implementato con il meccanismo di RMI callback.
Il client mantiene una struttura dati per tenere traccia della lista degli utenti registrati e il loro stato (online/offline), la lista viene quindi aggiornata a seguito della ricezione di una callback, attraverso la quale il server manda gli aggiornamenti: nuove registrazioni, cambiamento di stato di utenti registrati (online/offline).
- Dopo previa login effettuata con successo, l’utente interagisce, secondo il modello client-server (richieste/risposte), con il server sulla connessione TCP creata, inviando i comandi elencati in precedenza.
Tutte le operazioni sono effettuate su questa connessione TCP, eccetto la registrazione (RMI), le operazioni di recupero della lista degli utenti (`listUsers` e `listOnlineusers`) che usano la struttura dati locale del client aggiornata tramite il meccanismo di RMI callback (come descritto al punto precedente) e le operazioni sulla chat.
- Il server può essere realizzato multithreaded oppure può effettuare il multiplexing dei canali mediante NIO.
- L'utente interagisce con WORTH mediante un client che può utilizzare una semplice interfaccia grafica, oppure una interfaccia a linea di comando, definendo un insieme di comandi, presentati in un menu.
- Deve essere implementata una struttura dati separata per ciascuna lista di progetto (ovvero quattro liste per progetto).
- La chat di progetto deve essere realizzata usando UDP multicast (un client può inviare direttamente i messaggi ad altri client).
Ogni chat di progetto ha un indirizzo IP multicast diverso, scelto dal server al momento della creazione del progetto.
La modalità con cui il server comunica ai client i riferimenti per unirsi alla chat è a scelta dello studente (da motivare nella relazione).
- Implementazione della chat: nel caso in cui si decida di implementare l'interfaccia grafica, essa prevederà due semplici aree di testo in cui rispettivamente inserire/ricevere i messaggi testuali inviati alla chat.
In questo caso, i messaggi vengono immediatamente presentati all'utente, mano a mano che vengono ricevuti.
Invece, nel caso si preferisca una interazione con WORTH a linea di comando, saranno definiti due comandi per, rispettivamente, inviare nuovi messaggi alla chat/ricevere tutti i messaggi ricevuti a partire dall'ultima esecuzione del comando di visualizzazione messaggi.
In questo caso, i messaggi vengono presentati all'utente in modo asincrono, su sua richiesta.
- Il server persiste lo stato del sistema, in particolare: le informazioni di registrazione, la lista dei progetti (inclusi membri, card e lo stato delle liste).
Lo stato dei progetti deve essere reso persistente sul file system come descritto di seguito: una directory per ogni progetto e un file per ogni card del progetto (sul file sono accodati gli eventi di spostamento relativi alla card).
I messaggi delle chat non devono essere persistiti.
Quando il server viene riavviato tali informazioni sono utilizzate per ricostruire lo stato del sistema.

[^1]: In alternativa, la lista degli utenti registrati e il loro stato (online, offline) può essere restituita dal server invece che nella risposta della login in quella alla registrazione della callback RMI.
