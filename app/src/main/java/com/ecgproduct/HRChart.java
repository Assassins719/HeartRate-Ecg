package com.ecgproduct;


import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by hero save_on 6/22/2017.
 */

public class HRChart extends View {
    private float mGraphMax = 200.f;
    private int mRedrawInterval = 1000;
    private int mRedrawPoints;

    static final int SWEEP_MODE = 0;
    static final int FLOW_MODE = 1;
    private int mLineColor;
    private float mLineSize = 4f;
    private int mGridColor;
    private int mArrowColor;

    private int mWindowSize;
    private int mWindowCount = 1;
    private int ONEWINDOW = 30;
    private LinkedBlockingDeque<Integer> mInputBuf;
    private Vector<Integer> mDrawingBuf;

    private Paint mPaint;

    private Paint mPaintGrid;
    private Paint mPaintRuler;
    private Paint mPaintArrow;


    private Paint mPaintSmallGrid;

    private Paint mMaskBarPaint;
    private int mDrawPosition;

    private Activity mActivity;

    private int mGraphMode = FLOW_MODE;
    private boolean mGrid = true;
    private boolean mArrow = false;
    private boolean isConnected = false;

    public HRChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        mActivity = (Activity) context;
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.ECGChart,
                0, 0);

        try {
            mLineColor = a.getColor(R.styleable.ECGChart_lineColor, Color.WHITE);
            mLineSize = a.getFloat(R.styleable.ECGChart_lineSize, 4f);
            mGridColor = a.getColor(R.styleable.ECGChart_gridColor, Color.argb(0x33, 0x00, 0xFF, 0x00));
            mArrowColor = a.getColor(R.styleable.ECGChart_arrowColor, Color.rgb(255, 255, 255));


            mGraphMode = a.getInt(R.styleable.ECGChart_graphMode, SWEEP_MODE);
            mGrid = a.getBoolean(R.styleable.ECGChart_grid, true);
            mArrow = a.getBoolean(R.styleable.ECGChart_arrow, false);

            mWindowSize = a.getColor(R.styleable.ECGChart_windowSize, ONEWINDOW * mWindowCount);

            mInputBuf = new LinkedBlockingDeque<>();
            mDrawingBuf = new Vector<>();

            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setStrokeWidth(mLineSize);
            mPaint.setColor(mLineColor);

            mPaintGrid = new Paint();
            mPaintGrid.setColor(mGridColor);
            mPaintGrid.setStrokeWidth(2f);
            mPaintSmallGrid = new Paint();
            mPaintSmallGrid.setColor(mGridColor);
            mPaintSmallGrid.setStrokeWidth(1f);

            mPaintArrow = new Paint();
            mPaintArrow.setColor(mArrowColor);
            mPaintArrow.setStrokeWidth(2);
            mPaintArrow.setStyle(Paint.Style.FILL);

            mPaintRuler = new Paint();
            mPaintRuler.setColor(Color.argb(0xAA, 0xFF, 0xFF, 0xFF));

            mMaskBarPaint = new Paint();
            mMaskBarPaint.setColor(Color.rgb(0x33, 0x33, 0x33));
            mMaskBarPaint.setStyle(Paint.Style.FILL);
        } finally {
            a.recycle();
        }
        init();
    }

    private void init() {
        mRedrawPoints = ONEWINDOW / (1000 / mRedrawInterval);
        for (int i = 0; i < mWindowSize; i++)
            mDrawingBuf.add(0);
    }

    public void setGraphMax(int m) {
        this.mGraphMax = m;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Log.d("drawing","drawing");
        float width = this.getWidth();
        float height = this.getHeight();


        float mapRatio = (float) width / (mWindowSize-1);
        int start = mDrawingBuf.get(0);
        for (int i = 1; i < mWindowSize; i++) {
            int end = mDrawingBuf.get(i);
            canvas.drawLine((i-1) * mapRatio, height - start / mGraphMax * height, (i) * mapRatio, height - end / mGraphMax * height, mPaint);
            start = end;
        }
        if (mGrid)
            drawGridLine(canvas);
    }

    public void setConnection(boolean isConnect) {
        if (!isConnect)
            mInputBuf.clear();
        isConnected = isConnect;
    }

    public void drawGridLine(Canvas canvas) {
        float width = this.getWidth();
        float height = this.getHeight();
        int gridXNumber = 8;
        int gridYNumber = 4;
        for (int i = 0; i < gridXNumber; i++) {
            canvas.drawLine(i * width / gridXNumber, 0, i * width / gridXNumber, height, mPaintGrid);
        }

        for (int i = 0; i < gridXNumber; i++) {
            for (int j = 0; j < 10; j++)
                canvas.drawLine(i * width / gridXNumber + j * width / gridXNumber / 10f, 0,
                        i * width / gridXNumber + j * width / gridXNumber / 10f, height, mPaintSmallGrid);
        }

        for (int i = 0; i < gridYNumber; i++) {
            canvas.drawLine(0, i * height / gridYNumber, width, i * height / gridYNumber, mPaintGrid);
        }

        for (int i = 0; i < gridYNumber; i++) {
            for (int j = 0; j < 10; j++)
                canvas.drawLine(0, i * height / gridYNumber + j * height / gridYNumber / 10f,
                        width, i * height / gridYNumber + j * height / gridYNumber / 10f, mPaintSmallGrid);
        }
    }

    public void addEcgData(int data) {
        Log.d("Drawingsize",mDrawingBuf.size()+"");
        mDrawingBuf.remove(0);
        mDrawingBuf.add(data);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                invalidate();
            }
        });
    }
    public void clearGraph(){
        for (int i = 0; i < mWindowSize; i++)
            addEcgData(0);
    }
}
