package org.telegram.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fixed 3:1 VGRAM banner cropper. The original image is never stored as the selected banner. */
public final class VgramBannerCropActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VGRAM-BannerCrop"); t.setDaemon(true); return t;
    });
    private BannerCropView cropView;
    private TextView status;
    private Button saveButton;
    private Uri source;
    private boolean saving;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        source = getIntent() == null ? null : getIntent().getData();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff111318);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        TextView title = new TextView(this);
        title.setText("Кадрирование баннера");
        title.setTextColor(0xfff5f3ed);
        title.setTextSize(22);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = new TextView(this);
        hint.setText("Формат 3:1 · перетаскивай изображение и используй щипок для масштаба. Оригинал не будет сохранён как баннер.");
        hint.setTextColor(0xffb8bbc2);
        hint.setTextSize(13);
        hint.setPadding(0, dp(8), 0, dp(16));
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        cropView = new BannerCropView();
        LinearLayout.LayoutParams cropParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        cropParams.gravity = Gravity.CENTER;
        root.addView(cropView, cropParams);
        status = new TextView(this);
        status.setText("Загрузка изображения…");
        status.setTextColor(0xffb8bbc2);
        status.setTextSize(12);
        status.setPadding(0, dp(12), 0, dp(10));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = new Button(this);
        cancel.setText("Отмена");
        cancel.setOnClickListener(v -> { setResult(RESULT_CANCELED); finish(); });
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(50), 1f));
        saveButton = new Button(this);
        saveButton.setText("Подтвердить обрезку");
        saveButton.setEnabled(false);
        saveButton.setOnClickListener(v -> saveCrop());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        saveParams.leftMargin = dp(8);
        actions.addView(saveButton, saveParams);
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        if (source == null || !"content".equalsIgnoreCase(source.getScheme())) {
            status.setText("Не удалось открыть исходное изображение.");
            return;
        }
        io.execute(() -> {
            Bitmap bitmap = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(source)) {
                    if (in != null) BitmapFactory.decodeStream(in, null, bounds);
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IllegalArgumentException("Invalid image");
                int sample = 1;
                int target = Math.max(1600, getResources().getDisplayMetrics().widthPixels * 2);
                while (bounds.outWidth / sample > target * 2 || bounds.outHeight / sample > target * 2) sample *= 2;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                try (InputStream in = getContentResolver().openInputStream(source)) {
                    if (in != null) bitmap = BitmapFactory.decodeStream(in, null, opts);
                }
                if (bitmap == null) throw new IllegalArgumentException("Decode failed");
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("Не удалось прочитать изображение. Выбери другой файл."));
                return;
            }
            Bitmap loaded = bitmap;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) { if (!loaded.isRecycled()) loaded.recycle(); return; }
                cropView.setBitmap(loaded);
                status.setText("Предпросмотр 3:1 готов. Подтверди кадрирование для сохранения.");
                saveButton.setEnabled(true);
            });
        });
    }

    private void saveCrop() {
        if (saving || cropView == null || !cropView.hasBitmap()) return;
        saving = true;
        saveButton.setEnabled(false);
        status.setText("Сохранение обрезанного изображения…");
        io.execute(() -> {
            File output = new File(getFilesDir(), "vgram_profile_banner.jpg");
            try {
                Bitmap cropped = cropView.createCroppedBitmap();
                if (cropped == null) throw new IllegalStateException("Crop is empty");
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    if (!cropped.compress(Bitmap.CompressFormat.JPEG, 90, stream)) throw new IllegalStateException("JPEG compression failed");
                    stream.flush();
                } finally {
                    if (cropped != cropView.getBitmap() && !cropped.isRecycled()) cropped.recycle();
                }
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    IntentResult.finish(this, Uri.fromFile(output));
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    saving = false;
                    saveButton.setEnabled(true);
                    status.setText("Не удалось сохранить обрезку. Попробуй ещё раз.");
                    Toast.makeText(this, "Ошибка кадрирования", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private static final class IntentResult {
        static void finish(Activity activity, Uri output) {
            android.content.Intent result = new android.content.Intent();
            result.setData(output);
            activity.setResult(RESULT_OK, result);
            activity.finish();
        }
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        if (cropView != null) cropView.release();
        super.onDestroy();
    }

    private final class BannerCropView extends View {
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final RectF bitmapBounds = new RectF();
        private final RectF mappedBounds = new RectF();
        private Bitmap bitmap;
        private float baseScale = 1f;
        private float lastX, lastY;
        private boolean scaling;
        private final ScaleGestureDetector scaleDetector;

        BannerCropView() {
            super(VgramBannerCropActivity.this);
            overlayPaint.setColor(0xFF111318);
            gridPaint.setColor(0x99FFFFFF);
            gridPaint.setStrokeWidth(dp(1));
            scaleDetector = new ScaleGestureDetector(VgramBannerCropActivity.this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    if (bitmap == null) return false;
                    float[] values = new float[9];
                    matrix.getValues(values);
                    float current = values[Matrix.MSCALE_X];
                    float target = Math.max(baseScale, Math.min(baseScale * 5f, current * detector.getScaleFactor()));
                    float factor = target / Math.max(0.001f, current);
                    matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                    clampMatrix();
                    invalidate();
                    return true;
                }
                @Override public boolean onScaleBegin(ScaleGestureDetector detector) { scaling = true; return true; }
                @Override public void onScaleEnd(ScaleGestureDetector detector) { scaling = false; }
            });
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        boolean hasBitmap() { return bitmap != null && !bitmap.isRecycled(); }
        Bitmap getBitmap() { return bitmap; }
        void setBitmap(Bitmap value) { bitmap = value; resetMatrix(); invalidate(); }
        void release() { if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); bitmap = null; }
        private void resetMatrix() {
            if (bitmap == null || getWidth() <= 0 || getHeight() <= 0) return;
            baseScale = Math.max(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
            matrix.reset();
            matrix.setScale(baseScale, baseScale);
            matrix.postTranslate((getWidth() - bitmap.getWidth() * baseScale) / 2f, (getHeight() - bitmap.getHeight() * baseScale) / 2f);
            clampMatrix();
        }
        private void clampMatrix() {
            if (bitmap == null) return;
            matrix.mapRect(mappedBounds, new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()));
            float dx = mappedBounds.left > 0 ? -mappedBounds.left : mappedBounds.right < getWidth() ? getWidth() - mappedBounds.right : 0;
            float dy = mappedBounds.top > 0 ? -mappedBounds.top : mappedBounds.bottom < getHeight() ? getHeight() - mappedBounds.bottom : 0;
            matrix.postTranslate(dx, dy);
        }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { resetMatrix(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(0xff191c22);
            if (bitmap == null || bitmap.isRecycled()) return;
            int save = canvas.save();
            canvas.clipRect(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(bitmap, matrix, imagePaint);
            float w = getWidth(), h = getHeight();
            canvas.drawLine(w / 3f, 0, w / 3f, h, gridPaint);
            canvas.drawLine(w * 2f / 3f, 0, w * 2f / 3f, h, gridPaint);
            canvas.drawLine(0, h / 2f, w, h / 2f, gridPaint);
            overlayPaint.setStyle(Paint.Style.STROKE);
            overlayPaint.setStrokeWidth(dp(2));
            overlayPaint.setColor(0xFFF0D990);
            canvas.drawRect(dp(1), dp(1), w - dp(1), h - dp(1), overlayPaint);
            overlayPaint.setStyle(Paint.Style.FILL);
            canvas.restoreToCount(save);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (bitmap == null) return false;
            scaleDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: lastX = event.getX(); lastY = event.getY(); return true;
                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress() && !scaling) {
                        matrix.postTranslate(event.getX() - lastX, event.getY() - lastY);
                        clampMatrix();
                        invalidate();
                    }
                    lastX = event.getX(); lastY = event.getY(); return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: scaling = false; performClick(); return true;
            }
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
        Bitmap createCroppedBitmap() {
            if (!hasBitmap() || getWidth() <= 0 || getHeight() <= 0) return null;
            Matrix inverse = new Matrix();
            if (!matrix.invert(inverse)) return null;
            RectF crop = new RectF(0, 0, getWidth(), getHeight());
            inverse.mapRect(crop);
            int left = Math.max(0, (int) Math.floor(crop.left));
            int top = Math.max(0, (int) Math.floor(crop.top));
            int right = Math.min(bitmap.getWidth(), (int) Math.ceil(crop.right));
            int bottom = Math.min(bitmap.getHeight(), (int) Math.ceil(crop.bottom));
            if (right <= left || bottom <= top) return null;
            return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
        }
    }
}
