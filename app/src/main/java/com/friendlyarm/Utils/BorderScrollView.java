package com.friendlyarm.Utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.ScrollView;


public class BorderScrollView extends ScrollView {
    public BorderScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint paint = new Paint();
        //  将边框设为黑色
        paint.setColor(android.graphics.Color.GRAY);
        //  画TextView的4个边
        canvas.drawLine(100, 0, this.getWidth() - 1, 0, paint);
        canvas.drawLine(100, 0, 0, this.getHeight() - 1, paint);
        canvas.drawLine(this.getWidth() - 1, 0, this.getWidth() - 1, this.getHeight() - 1, paint);
        canvas.drawLine(100, this.getHeight() - 1, this.getWidth() - 1, this.getHeight() - 1, paint);
    }
}