package com.rossiv.myrj.attendance;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.nxp.nfclib.CardType;
import com.nxp.nfclib.NxpNfcLib;
import com.nxp.nfclib.desfire.DESFireFactory;
import com.nxp.nfclib.desfire.IDESFireEV1;
import com.nxp.nfclib.exceptions.NxpNfcLibException;
import com.nxp.nfclib.utils.NxpLogUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    /**
     * NxpNfclib setup.
     */
    private String nxpPackageKey = "109e31c15065b86942300046ad621ae5";
    private NxpNfcLib libInstance = null;
    private IDESFireEV1 desFireEV1 = null;

    private Spinner spinnerAttendableType;
    private EditText numberAttendableId;
    private Button buttonSave;
    private Button buttonClear;
    private Button buttonExport;

    private ArrayList<ArrayList<String>> buzzcardQueue = new ArrayList<>();
    private ArrayList<String> buzzcardQueue2 = new ArrayList<>();

    public static final String TAG = "MyRJ-Attendance";

    /**
     * Constant for permission
     */
    private static final int STORAGE_PERMISSION_WRITE = 113;
    private static final String UNABLE_TO_READ = "Unable to read";
    private static final char TOAST_PRINT = 'd';
    private static final char TOAST = 't';
    private static final char PRINT = 'n';
    private static final String EMPTY_SPACE = " ";

    /**
     * Initialize the Mifare library and register to this activity.
     */
    private void initializeMifareLibrary() {
        libInstance = NxpNfcLib.getInstance();
        try {
            libInstance.registerActivity(this, nxpPackageKey);
        } catch (NxpNfcLibException ex) {
            showMessage(ex.getMessage(), TOAST);
        } catch (Exception e) {
            // do nothing added to handle the crash if any
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        boolean readPermission = (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);

        if (!readPermission) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_WRITE
            );
        }

        initializeMifareLibrary();
        addListenerOnButtonSave();
        addListenerOnButtonClear();
        addListenerOnButtonExport();
    }

    @Override
    protected void onResume() {
        libInstance.startForeGroundDispatch();
        super.onResume();
    }

    @Override
    protected void onPause() {
        libInstance.stopForeGroundDispatch();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public void onNewIntent(final Intent intent) {
        cardLogic(intent);
        super.onNewIntent(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void cardLogic(final Intent intent) {
        CardType cardType = libInstance.getCardType(intent);
        if (CardType.DESFireEV1 == cardType) {
            desFireEV1 = DESFireFactory.getInstance().getDESFire(libInstance.getCustomModules());
            try {
                desFireEV1.getReader().connect();
                readBuzzCardData();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } else if (CardType.UnknownCard == cardType) {
            showMessage(getString(R.string.UNKNOWN_TAG), TOAST);
        } else {
            showMessage(getString(R.string.UNSUPPORTED_TAG), TOAST);
        }
    }

    public void readBuzzCardData() {
        // Initialize settings
        final int buzz_application = 0xBBBBCD;
        final int buzz_file = 0x01;
        byte[] buzzData = new byte[48];
        String buzzString = null;

        StringBuilder stringBuilder = new StringBuilder();

        // Read data
        try {
            desFireEV1.selectApplication(buzz_application);
            buzzData = desFireEV1.readData(buzz_file, 0, buzzData.length);
            buzzString = new String(buzzData, StandardCharsets.UTF_8);
            String[] buzzStringParts = buzzString.split("=");
            String gtid = buzzStringParts[0];
            String prox = buzzStringParts[1];
            stringBuilder.append("\nGTID: ").append(gtid);
            stringBuilder.append("\nProx: ").append(prox);

            String currentDateandTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            buzzcardQueue.add(new ArrayList<>(Arrays.asList(currentDateandTime, gtid)));
            buzzcardQueue2.add(gtid);
        } catch (Exception e) {
            stringBuilder.append(e.getMessage());
        }

        showMessage(stringBuilder.toString(), TOAST);
        NxpLogUtils.save();
    }

    public void addListenerOnButtonSave() {
        spinnerAttendableType = (Spinner) findViewById(R.id.spinnerAttendableType);
        numberAttendableId = (EditText) findViewById(R.id.numberAttendableId);
        buttonSave = (Button) findViewById(R.id.buttonSave);
        buttonSave.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this,
                        "Type : "+ String.valueOf(spinnerAttendableType.getSelectedItem()) +
                                "\nID : "+ numberAttendableId.getText().toString(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void addListenerOnButtonClear() {
        buttonClear = (Button) findViewById(R.id.buttonClear);
        buttonClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buzzcardQueue.clear();
                Toast.makeText(MainActivity.this, "Cleared Queue", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void addListenerOnButtonExport() {
        buttonExport = (Button) findViewById(R.id.buttonExport);
        buttonExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveBuzzCardQueueToFile(buzzcardQueue2);
                Toast.makeText(MainActivity.this, "Exported to buzzcard_queue.txt", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveBuzzCardQueueToFile(ArrayList<String> arrayList) {
        try {
            FileOutputStream fileOutputStream = openFileOutput("buzzard_queue.txt", Context.MODE_PRIVATE);
            ObjectOutputStream out = new ObjectOutputStream(fileOutputStream);
            out.writeObject(arrayList);
            out.close();
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This will display message in toast or logcat or on screen or all three.
     *
     * @param str           String to be logged or displayed
     * @param operationType 't' for Toast; 'n' for Logcat and Display in UI; 'd' for Toast, Logcat
     *                      and
     *                      Display in UI.
     */
    protected void showMessage(final String str, final char operationType) {
        switch (operationType) {
            case TOAST:
                Toast.makeText(MainActivity.this, str, Toast.LENGTH_SHORT)
                        .show();
                break;
            case PRINT:
//                information_textView.setText(str);
//                information_textView.setGravity(Gravity.START);
                NxpLogUtils.i(TAG, getString(R.string.card_data) + str);
                break;
            case TOAST_PRINT:
                Toast.makeText(MainActivity.this, "\n" + str, Toast.LENGTH_SHORT).show();
//                information_textView.setText(str);
//                information_textView.setGravity(Gravity.START);
                NxpLogUtils.i(TAG, "\n" + str);
                break;
            default:
                break;
        }
    }

}
