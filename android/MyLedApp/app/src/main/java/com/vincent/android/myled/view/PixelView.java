package com.vincent.android.myled.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;


public class PixelView extends View {
    private static final int GRID_SIZE = 64;
    private Paint paint;
    private boolean[][] pixels;
    private boolean isDragMode = false;
    private float scaleFactor = 1.0f;
    private float translateX = 0;
    private float translateY = 0;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private PixelViewListener pixelViewListener;

    public PixelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setListener(PixelViewListener pixelViewListener) {
        this.pixelViewListener = pixelViewListener;
    }

    private void init() {
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeWidth(1);
        pixels = new boolean[GRID_SIZE][GRID_SIZE];
        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        gestureDetector = new GestureDetector(getContext(), new GestureListener());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        canvas.scale(scaleFactor, scaleFactor);
        canvas.translate(translateX / scaleFactor, translateY / scaleFactor);

        int cellSize = getWidth() / GRID_SIZE;

        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                paint.setColor(pixels[i][j] ? Color.WHITE : Color.BLACK);
                canvas.drawRect(i * cellSize, j * cellSize, (i + 1) * cellSize, (j + 1) * cellSize, paint);
            }
        }

        paint.setColor(Color.WHITE);
        for (int i = 0; i <= GRID_SIZE; i++) {
            canvas.drawLine(i * cellSize, 0, i * cellSize, getHeight(), paint);
            canvas.drawLine(0, i * cellSize, getWidth(), i * cellSize, paint);
        }

        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (isDragMode) {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
        } else {

            int cellSize = getWidth() / GRID_SIZE;
            int x = (int) ((event.getX() - translateX) / (cellSize * scaleFactor));
            int y = (int) ((event.getY() - translateY) / (cellSize * scaleFactor));

            if (x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        pixels[x][y] = !pixels[x][y];
                        invalidate();

                        if (pixelViewListener != null) {
                            pixelViewListener.pixelOnTouch(x, y, pixels[x][y] ? 1 :
                                    0);
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        break;
                }
            }
        }

        return true;
    }

    public void setDragMode(boolean dragMode) {
        isDragMode = dragMode;
    }

    public boolean isDragMode() {
        return isDragMode;
    }

    public void fillAllBlack() {
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                pixels[i][j] = false;
            }
        }
        invalidate();
    }

    public boolean isAllBlack() {
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                if (pixels[i][j] != false) {
                    return false;
                }
            }
        }
        return true;

    }

    public boolean isAllWhite() {
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                if (pixels[i][j] != true) {
                    return false;
                }
            }
        }
        return true;

    }

    public void fillAllWhite() {
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                pixels[i][j] = true;
            }
        }
        invalidate();
    }

    public void resetView() {
        scaleFactor = 1.0f;
        translateX = 0;
        translateY = 0;
        invalidate();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 10.0f));
            invalidate();
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (isDragMode) {
                resetView();
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (isDragMode) {
                translateX -= distanceX;
                translateY -= distanceY;
                invalidate();
            }
            return true;
        }
    }

    public byte[] getC51ModData() {
        byte[] modData = new byte[(GRID_SIZE * GRID_SIZE) / 8];
        int index = 0;
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x += 8) {
                byte byteData = 0;
                for (int bit = 0; bit < 8; bit++) {
                    if (((x + bit) < GRID_SIZE) && ((pixels[x + bit][y]))) {
                        byteData |= (1 << (7 - bit));
                    }
                }
                modData[index++] = byteData;
            }
        }
        return modData;
    }
}