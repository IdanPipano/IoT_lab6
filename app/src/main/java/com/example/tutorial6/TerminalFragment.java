package com.example.tutorial6;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;
    private TextUtil.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;
    private Spinner spinner;

    LineChart mpLineChart;
    //LineDataSet lineDataSet1;
    LineDataSet lineDataSetX;
    LineDataSet lineDataSetY;
    LineDataSet lineDataSetZ;
    ArrayList<ILineDataSet> dataSets = new ArrayList<>();
    LineData data;

    private String actualCountOfSteps;  // an integer the user enters before saving
    private EditText editTxtSteps;
    private EditText editTxtFileName;
    private RadioGroup radioGroupMovementType;
    private RadioButton chosenMovement;
    private boolean recording = false;

    private LinearLayout containerLL;
    private ArrayList<String> filesList;
    private String fileToOpen;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d("wtf", "hiiii");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");

    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        sendText = view.findViewById(R.id.send_text);
        hexWatcher = new TextUtil.HexWatcher(sendText);
        hexWatcher.enable(hexEnabled);
        sendText.addTextChangedListener(hexWatcher);
        sendText.setHint(hexEnabled ? "HEX mode" : "");

        editTxtSteps = view.findViewById(R.id.editTxtSteps);
        editTxtFileName = view.findViewById(R.id.editTxtFileName);
        radioGroupMovementType = view.findViewById(R.id.radioGroupMovementType);

        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));

        mpLineChart = (LineChart) view.findViewById(R.id.line_chart);
        //lineDataSet1 =  new LineDataSet(emptyDataValues(), "temperature");

        containerLL = view.findViewById(R.id.idLLContainer);
        filesList = new ArrayList<>();

        lineDataSetX =  new LineDataSet(emptyDataValues(),"X");
        lineDataSetY =  new LineDataSet(emptyDataValues(),"Y");
        lineDataSetZ =  new LineDataSet(emptyDataValues(),"Z");

        lineDataSetX.setColor(Color.RED);
        lineDataSetX.setCircleColor(Color.RED);
        lineDataSetY.setColor(Color.GREEN);
        lineDataSetY.setCircleColor(Color.GREEN);
        lineDataSetZ.setColor(Color.MAGENTA);
        lineDataSetZ.setCircleColor(Color.MAGENTA);
        //LineDataSet lineDataSet2 =  new LineDataSet(dataValues1(), "Data Set 2");

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(lineDataSetX);
        dataSets.add(lineDataSetY);
        dataSets.add(lineDataSetZ);
        data = new LineData(dataSets);
        mpLineChart.setData(data);
        mpLineChart.invalidate();

        //spinner = (Spinner) view.findViewById(R.id.fileSpinner);

        //dataSets.add(lineDataSet1);
        //data = new LineData(dataSets);
        //mpLineChart.setData(data);
        //mpLineChart.invalidate();

        Button buttonClear = (Button) view.findViewById(R.id.button1);
        Button buttonCsvShow = (Button) view.findViewById(R.id.button2);
        Button buttonSave = (Button) view.findViewById(R.id.btn_save);
        Button buttonStart = (Button) view.findViewById(R.id.btn_start);
        Button buttonStop = (Button) view.findViewById(R.id.btn_stop);
        Button buttonReset = (Button) view.findViewById(R.id.btn_reset);
        //Spinner spinner = (Spinner) view.findViewById(R.id.fileSpinner);

        radioGroupMovementType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                chosenMovement = (RadioButton) view.findViewById(i);

            }
        });

        File dir = new File("/sdcard/csv_dir/");
        Log.d("wtf", "after new File");
        filesList = new ArrayList<>(Arrays.asList(dir.list()));
        filesList = new ArrayList<>(filesList.subList(1, filesList.size()));

        for (String f:
             filesList) {
            Log.d("wtf", f);
        }


        // on below line we are creating layout params for text view.
        // and specifying width as match parent and height as wrap content
        LinearLayout.LayoutParams txtLayoutParam = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        // on below line we are adding gravity
        txtLayoutParam.gravity = Gravity.CENTER;

        // on below line we are creating layout params for spinner.
        // and specifying width as wrap parent and height as wrap content
        LinearLayout.LayoutParams spinnerLayoutParam = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        // on below line we are adding gravity
        spinnerLayoutParam.gravity = Gravity.CENTER;

        // on below line we are creating our dynamic text view
        TextView headingTV = new TextView(getContext());

        // on below line we are setting for our text view.
        headingTV.setText("Choose file:");

        // on below line we are updating text size.
        headingTV.setTextSize(5f);

        // on below line we are updating text color and font
        headingTV.setTextColor(getResources().getColor(R.color.black));
        headingTV.setTypeface(Typeface.DEFAULT_BOLD);

        // on below line we are adding padding
        headingTV.setPadding(20, 20, 20, 20);

        // on below line we are specifying text alignment.
        headingTV.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        // on below line we are adding layout param
        // for heading text view.
        headingTV.setLayoutParams(txtLayoutParam);

        // create spinner programmatically
        Spinner spinner = new Spinner(getContext());

        // on below line we are adding params for spinner.
        spinner.setLayoutParams(spinnerLayoutParam);

        // on below line we are adding our
        // views to container linear layout
        containerLL.addView(headingTV);
        containerLL.addView(spinner);

        // on below line we are checking if spinner is not null
        if (spinner != null) {
            // on below line we are initializing and setting our adapter
            // to our spinner.
            ArrayAdapter adapter = new ArrayAdapter(getContext(), android.R.layout.simple_spinner_item, filesList);
            spinner.setAdapter(adapter);
            // on below line we are adding on item selected listener for spinner.
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    // in on selected listener we are displaying a toast message
                    Toast.makeText(getContext(), "Selected Language is : " + filesList.get(position), Toast.LENGTH_SHORT).show();
                    // TODO: context?
                    fileToOpen = filesList.get(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
        }





















        buttonClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(getContext(),"Clear",Toast.LENGTH_SHORT).show();
                LineData data = mpLineChart.getData();
                for (int i = 0; i < 3; ++i) {
                    ILineDataSet set = data.getDataSetByIndex(i);
                    //data.getDataSetByIndex(i);
                    while(set.removeLast()){}
                }
//                ILineDataSet set = data.getDataSetByIndex(0);
//                //data.getDataSetByIndex(0);
//                while(set.removeLast()){}


            }
        });


        buttonStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(getContext(),"Start Recording",Toast.LENGTH_SHORT).show();
                recording = true;
            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(getContext(),"Stop Recording",Toast.LENGTH_SHORT).show();
                recording = false;
            }
        });

        buttonReset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(getContext(),"Reset",Toast.LENGTH_SHORT).show();
                File file = new File("/sdcard/csv_dir/record.csv");
                file.delete();
            }
        });

        buttonCsvShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OpenLoadCSV();

            }
        });

        buttonSave.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View view) {
                Toast.makeText(getContext(),"Save",Toast.LENGTH_SHORT).show();
                actualCountOfSteps = editTxtSteps.getText().toString();
                String movementType = (String) chosenMovement.getText();
                String fileName = editTxtFileName.getText().toString();
                filesList.add(fileName); //for the spinner
                File file = new File("/sdcard/csv_dir/");
                file.mkdirs();
                String csv = "/sdcard/csv_dir/" + fileName + ".csv";
                CSVWriter csvWriter = null;
                try {
                    csvWriter = new CSVWriter(new FileWriter(csv,true));
                    String[] row = new String[]{"NAME:", fileName + ".csv"};
                    csvWriter.writeNext(row);

                    row = new String[]{"EXPERIMENT TIME:", DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(LocalDateTime.now())};
                    csvWriter.writeNext(row);

                    row = new String[]{"ACTIVITY TYPE:", movementType};
                    csvWriter.writeNext(row);

                    row = new String[]{"COUNT OF ACTUAL STEPS:", actualCountOfSteps+""};
                    csvWriter.writeNext(row);

                    row = new String[]{};
                    csvWriter.writeNext(row);

                    row = new String[]{"Time [sec]","ACC X","ACC Y","ACC Z"};
                    csvWriter.writeNext(row);

                    ArrayList<String[]> csvData = new ArrayList<>();
                    csvData= CsvRead("/sdcard/csv_dir/record.csv");
                    for (int i = 0; i < csvData.size(); i++){
                        float xValue = Float.parseFloat(csvData.get(i)[0]);
                        float yValue = Float.parseFloat(csvData.get(i)[1]);
                        float zValue = Float.parseFloat(csvData.get(i)[2]);
                        float tValue = Float.parseFloat(csvData.get(i)[3]);
                        row = new String[]{tValue+"",xValue+"",yValue+"",zValue+""};
                        csvWriter.writeNext(row);
                    }

                    csvWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                buttonReset.callOnClick();
            }
        });

        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            sendText.setText("");
            hexWatcher.enable(hexEnabled);
            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private String[] clean_str(String[] stringsArr){
         for (int i = 0; i < stringsArr.length; i++)  {
             stringsArr[i]=stringsArr[i].replaceAll(" ","");
        }


        return stringsArr;
    }
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] message) {


        if(hexEnabled) {
            receiveText.append(TextUtil.toHexString(message) + '\n');
        }
        else
        {
            String msg = new String(message);
//          Log.d("wtf", "in receive: " + TextUtil.toCaretString(msg, newline.length() != 0));

            if(newline.equals(TextUtil.newline_crlf) && msg.length() > 0)
            {
                // don't show CR as ^M if directly before LF
                String msg_to_save = msg;
                msg_to_save = msg.replace(TextUtil.newline_crlf, TextUtil.emptyString);
                // check message length
                if (msg_to_save.length() > 1){
                    // split message string by ',' char
                    String[] parts = msg_to_save.split(",");
                    // function to trim blank spaces
                    parts = clean_str(parts);
                    receiveText.append("X: " + parts[0] + ", Y: " + parts[1] + ", Z: " + parts[2] + ", t: " + parts[3] + "\n");
                    // saving data to csv
                    try {
                        if(recording==true){
                            // create new csv unless file already exists
                            File file = new File("/sdcard/csv_dir/");
                            file.mkdirs();
                            String csv = "/sdcard/csv_dir/record.csv";
                            CSVWriter csvWriter = new CSVWriter(new FileWriter(csv,true));
                            // parse string values, in this case [0] is tmp & [1] is count (t)
                            String row[]= new String[]{parts[0],parts[1], parts[2], parts[3]};
                            csvWriter.writeNext(row);
                            csvWriter.close();
                        }

                        // add received values to line datasets for plotting the linechart
                        data.addEntry(new Entry(Float.parseFloat(parts[3]),Float.parseFloat(parts[0])), 0);
                        data.addEntry(new Entry(Float.parseFloat(parts[3]),Float.parseFloat(parts[1])), 1);
                        data.addEntry(new Entry(Float.parseFloat(parts[3]),Float.parseFloat(parts[2])), 2);
                        lineDataSetX.notifyDataSetChanged(); // let the data know a dataSet changed
                        lineDataSetY.notifyDataSetChanged(); // let the data know a dataSet changed
                        lineDataSetZ.notifyDataSetChanged(); // let the data know a dataSet changed

                        mpLineChart.notifyDataSetChanged(); // let the chart know it's data changed
                        mpLineChart.invalidate(); // refresh
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                // send msg to function that saves it to csv
                // special handling if CR and LF come in separate fragments
                if (pendingNewline && msg.charAt(0) == '\n') {
                    Editable edt = receiveText.getEditableText();
                    if (edt != null && edt.length() > 1)
                        edt.replace(edt.length() - 2, edt.length(), "");
                }
                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }
//            receiveText.append(TextUtil.toCaretString(msg, newline.length() != 0));
            //TODO: they placed the above line outside if - we inside. problematic?

        }
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        try {
        receive(data);}
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    private ArrayList<Entry> emptyDataValues()
    {
        ArrayList<Entry> dataVals = new ArrayList<Entry>();
        return dataVals;
    }

    private void OpenLoadCSV(){
        Intent intent = new Intent(getContext(),LoadCSV.class);
        Bundle b = new Bundle();
        b.putString("fileName", fileToOpen);
        intent.putExtras(b);
        startActivity(intent);
    }

    private ArrayList<String[]> CsvRead(String path){
        ArrayList<String[]> CsvData = new ArrayList<>();
        try {
            File file = new File(path);
            CSVReader reader = new CSVReader(new FileReader(file));
            String[]nextline;
            while((nextline = reader.readNext())!= null){
                if(nextline != null){
                    CsvData.add(nextline);

                }
            }

        }catch (Exception e){}
        return CsvData;
    }


}
