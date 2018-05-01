package geog477.fooddesert;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

/**
 * Created by john on 4/28/18.
 * This class can be used to display a dialog with a simple message ("Location is (not)? in a food desert").
 * I haven't got around to implementing the events that should use this Fragment.
 */

public class FoodDesertStatusDialogFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState){

        Bundle args = getArguments();

        boolean inFoodDesert = args.getBoolean("inFoodDesert");

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        if (inFoodDesert){
            builder.setMessage(R.string.in_food_desert);
        } else {
            builder.setMessage(R.string.not_in_food_desert);
        }

        return builder.create();
    }
}
