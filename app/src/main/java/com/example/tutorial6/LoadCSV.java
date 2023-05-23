package com.example.tutorial6;


import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import com.opencsv.CSVReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;

import java.util.Arrays;
import java.util.List;
// hi

public class LoadCSV extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_csv);
        Bundle b = getIntent().getExtras();
        String fileName = b.getString("fileName");
        Button BackButton = (Button) findViewById(R.id.button_back);
        LineChart lineChart = (LineChart) findViewById(R.id.line_chart);

        ArrayList<String[]> csvData = new ArrayList<>();
        csvData= CsvRead("/sdcard/csv_dir/"+fileName);
        csvData = new ArrayList<>(csvData.subList(6, csvData.size()));


//        Button BackButton = (Button) findViewById(R.id.button_back);
//        LineChart lineChart = (LineChart) findViewById(R.id.line_chart);
//
//        ArrayList<String[]> csvData = new ArrayList<>();

//        csvData= CsvRead("/sdcard/csv_dir/record.csv");


        LineDataSet lineDataSetX =  new LineDataSet(DataValues(csvData, 1, 0),"X");
        LineDataSet lineDataSetY =  new LineDataSet(DataValues(csvData, 2, 0),"Y");
        LineDataSet lineDataSetZ =  new LineDataSet(DataValues(csvData, 3, 0),"Z");

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
        LineData data = new LineData(dataSets);
        lineChart.setData(data);
        lineChart.invalidate();




        BackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClickBack();
            }
        });

    }

    private void ClickBack(){
        finish();

    }

    private ArrayList<String[]> CsvRead(String path){
        ArrayList<String[]> CsvData = new ArrayList<>();
        try {
            File file = new File(path);
            CSVReader reader = new CSVReader(new FileReader(file));
            String[] nextline;
            while((nextline = reader.readNext())!= null){
                if(nextline != null){
                    CsvData.add(nextline);

                }
            }

        }catch (Exception e){}
        return CsvData;
    }

    private ArrayList<Entry> DataValues(ArrayList<String[]> csvData, int axis, int timeIndex){
        // timeIndex was 3
        ArrayList<Entry> dataVals = new ArrayList<Entry>();
        for (int i = 0; i < csvData.size(); i++){
            dataVals.add(new Entry(Float.parseFloat(csvData.get(i)[timeIndex]),
                    Float.parseFloat(csvData.get(i)[axis])));

        }


        return dataVals;
    }

}