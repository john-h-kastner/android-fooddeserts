package geog477.fooddesert;

/**
 * Created by john on 5/8/18.
 */

public enum FoodDesertStatus {

    IN_FOOD_DESERT (R.string.in_food_desert),
    NOT_IN_FOOD_DESERT (R.string.not_in_food_desert),
    NO_DATA (R.string.no_data);

    private final int statusStringResource;
    FoodDesertStatus(int statusStringResource){
        this.statusStringResource = statusStringResource;
    }

    public int getStatusStringResource(){
        return this.statusStringResource;
    }
}