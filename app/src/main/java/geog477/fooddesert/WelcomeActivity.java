package geog477.fooddesert;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.View;
import android.widget.Button;

import android.widget.TextView;

/**
 * Created by john on 5/9/18.
 */

public class WelcomeActivity extends AppCompatActivity {

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        TextView view = (TextView)findViewById(R.id.textBody);
        String formattedText = "You can find information about food deserts <a href='https://www.ers.usda.gov/data-products/food-access-research-atlas/documentation/'>here</a>";
        view.setText(Html.fromHtml(formattedText));
        initMapButton();


    }

    private void initMapButton() {
        Button list = (Button) findViewById(R.id.go_to_map_btn);
        list.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(WelcomeActivity.this, ArcMapActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }

        });
    }

}
