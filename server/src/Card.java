import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;

public class Card {

    /* OVERVIEW: modella le card di un progetto
     *              - name: nome della card
     *              - description: descrizione della card
     *              - story: sequenza di eventi di spostamento della card */

    private String name;
    private String description;
    private ArrayList<CardState> story;

                                        //METODI COSTRUTTORE

    public Card(String name, String description) {
        if (name == null) throw new NullPointerException("Invalid card name");
        if (description == null) throw new NullPointerException("Invalid description");
        this.name = name;
        this.description = description;
        this.story = new ArrayList<>();
        this.story.add(CardState.ToDo);

    }
    public Card(){}

    //-------------------------------------------------------------------------------------//

                                        //METODI GETTER/SETTER

    public String getDescription() { return description; }

    public ArrayList<CardState> getStory() { return story; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public void setDescription(String description) { this.description = description; }

    public void setStory(ArrayList<CardState> story) {this.story = story; }

    //-------------------------------------------------------------------------------------//

                                        //METODI D'ISTANZA

    //EFFECTS: Aggiunge un evento di spostamento alla lista di eventi
    public void addToStory (CardState nameList){
        this.story.add(nameList);
    }

    /* EFFECTS: crea una stringa che rappresenta la storia di this.card
     * RETURN: la storia di this.card*/

    @JsonIgnore
    public String getHistory (){
        StringBuilder story = new StringBuilder();
        CardState nameList;
        for (int i = 0; i < this.story.size(); i++) {
            nameList = this.story.get(i);
            story.append(nameList.toString());
            if(i<this.story.size()-1){
                story.append(" -> ");
            }
        }
        return story.toString();
    }

    /* EFFECTS: crea una stringa con le informazioni di this.card
     * RETURN:  nome, descrizione e lista attuale di this.card */
    @JsonIgnore
    public String getInformation (){
        return "Name: " + this.name + System.lineSeparator() +
                "Description: " + this.description + System.lineSeparator() +
                "List: "+(this.story.get(story.size() - 1)).toString() + System.lineSeparator();
    }


}
