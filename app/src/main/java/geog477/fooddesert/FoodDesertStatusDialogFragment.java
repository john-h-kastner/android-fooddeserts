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

    private static final String FOOD_DESERT_STATUS = "foodDesertStatus";

    public static FoodDesertStatusDialogFragment newInstance(FoodDesertStatus status){
        Bundle args = new Bundle();
        args.putInt(FOOD_DESERT_STATUS, status.getStatusStringResource());

        FoodDesertStatusDialogFragment fragment = new FoodDesertStatusDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState){
        Bundle args = getArguments();
        int statusStringResource = args.getInt(FOOD_DESERT_STATUS);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(statusStringResource);

        return builder.create();
    }
}
