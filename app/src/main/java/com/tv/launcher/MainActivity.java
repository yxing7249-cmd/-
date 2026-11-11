package com.tv.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final String TAG = "TVLauncher";
    private static final int SHIZUKU_CODE = 1001;
    private static final int REQUEST_PICK_IMAGE = 2001;

    private static final String PREF_RECENT = "recent_apps";
    private static final String PREF_LAST_FOCUS = "last_focus_package";
    private static final String PREF_WALLPAPER = "wallpaper_uri";

    private TextView tvTime;
    private LinearLayout categoryContainer;
    private RecyclerView appRecycler;
    private RecyclerView recentRecycler;
    private ImageView wallpaperView;
    private View wallpaperScrim;

    private AppAdapter adapter;
    private RecentAdapter recentAdapter;

    private final List<AppInfo> appList = new ArrayList<>();
    private final List<AppInfo> recentList = new ArrayList<>();
    private final List<TextView> categoryViews = new ArrayList<>();
    private final Handler timeHandler = new Handler(Looper.getMainLooper());

    private int currentCategoryIndex = 0;
    private String lastFocusPackage = null;

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode == SHIZUKU_CODE) {
                    String msg = grantResult == PackageManager.PERMISSION_GRANTED
                            ? "Shizuku 授权成功" : "Shizuku 授权被拒绝";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        tvTime = findViewById(R.id.tvTime);
        categoryContainer = findViewById(R.id.categoryContainer);
        appRecycler = findViewById(R.id.appRecycler);
        recentRecycler = findViewById(R.id.recentRecycler);
        wallpaperView = findViewById(R.id.wallpaperView);
        wallpaperScrim = findViewById(R.id.wallpaperScrim);

        setupCategories();
        setupRecycler();
        setupRecentRecycler();
        loadApps();
        loadRecentApps();
        loadWallpaper();
        startTimeUpdate();

        lastFocusPackage = getSharedPreferences("launcher", MODE_PRIVATE)
                .getString(PREF_LAST_FOCUS, null);

        Shizuku.addRequestPermissionResultListener(permissionResultListener);

        appRecycler.postDelayed(this::restoreLastFocus, 400);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeHandler.removeCallbacksAndMessages(null);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
    }

    private void startTimeUpdate() {
        Runnable update = new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                tvTime.setText(sdf.format(new Date()));
                timeHandler.postDelayed(this, 30_000);
            }
        };
        timeHandler.post(update);
    }

    private void setupCategories() {
        String[] categories = {"主屏幕", "应用", "壁纸", "设置", "Shizuku"};
        categoryViews.clear();
        categoryContainer.removeAllViews();

        for (int i = 0; i < categories.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(categories[i]);
            tv.setTextSize(18);
            tv.setPadding(32, 14, 32, 14);
            tv.setFocusable(true);
            tv.setFocusableInTouchMode(true);
            tv.setClickable(true);

            final int index = i;
            updateCategoryStyle(tv, index == 0);

            tv.setOnClickListener(v -> selectCategory(index));
            tv.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) selectCategory(index);
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 18, 0);
            categoryContainer.addView(tv, lp);
            categoryViews.add(tv);
        }
    }

    private void selectCategory(int index) {
        currentCategoryIndex = index;
        for (int i = 0; i < categoryViews.size(); i++) {
            updateCategoryStyle(categoryViews.get(i), i == index);
        }

        switch (index) {
            case 2:
                showWallpaperMenu();
                break;
            case 3:
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show();
                }
                break;
            case 4:
                showShizukuMenu();
                break;
        }
    }

    private void updateCategoryStyle(TextView tv, boolean selected) {
        if (selected) {
            tv.setTextColor(0xFFFFFFFF);
            tv.setBackgroundResource(R.drawable.bg_category_selected);
        } else {
            tv.setTextColor(0xFF888888);
            tv.setBackground(null);
        }
    }

    private void setupRecycler() {
        GridLayoutManager glm = new GridLayoutManager(this, 5);
        appRecycler.setLayoutManager(glm);
        appRecycler.setHasFixedSize(true);
        appRecycler.setItemViewCacheSize(20);
        appRecycler.setFocusable(true);
        appRecycler.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        adapter = new AppAdapter();
        appRecycler.setAdapter(adapter);
    }

    private void setupRecentRecycler() {
        LinearLayoutManager llm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recentRecycler.setLayoutManager(llm);
        recentRecycler.setHasFixedSize(true);
        recentRecycler.setFocusable(true);
        recentRecycler.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        recentAdapter = new RecentAdapter();
        recentRecycler.setAdapter(recentAdapter);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);
        appList.clear();
        for (ResolveInfo info : resolveInfos) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            AppInfo app = new AppInfo();
            app.packageName = pkg;
            app.name = info.loadLabel(pm).toString();
            app.icon = info.loadIcon(pm);
            appList.add(app);
        }
        appList.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        adapter.notifyDataSetChanged();
    }

    private void loadRecentApps() {
        recentList.clear();
        String saved = getSharedPreferences("launcher", MODE_PRIVATE).getString(PREF_RECENT, "");
        if (saved.isEmpty()) {
            recentAdapter.notifyDataSetChanged();
            return;
        }
        PackageManager pm = getPackageManager();
        for (String pkg : saved.split(",")) {
            if (pkg.isEmpty()) continue;
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                AppInfo app = new AppInfo();
                app.packageName = pkg;
                app.name = pm.getApplicationLabel(info).toString();
                app.icon = pm.getApplicationIcon(info);
                recentList.add(app);
            } catch (Exception ignored) {}
        }
        recentAdapter.notifyDataSetChanged();
    }

    private void addToRecent(String packageName) {
        List<String> list = new ArrayList<>();
        String saved = getSharedPreferences("launcher", MODE_PRIVATE).getString(PREF_RECENT, "");
        if (!saved.isEmpty()) {
            for (String s : saved.split(",")) {
                if (!s.equals(packageName) && !s.isEmpty()) list.add(s);
            }
        }
        list.add(0, packageName);
        while (list.size() > 12) list.remove(list.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) sb.append(",");
            sb.append(s);
        }
        getSharedPreferences("launcher", MODE_PRIVATE).edit().putString(PREF_RECENT, sb.toString()).apply();
        loadRecentApps();
    }

    private void saveLastFocus(String packageName) {
        if (packageName == null) return;
        lastFocusPackage = packageName;
        getSharedPreferences("launcher", MODE_PRIVATE).edit().putString(PREF_LAST_FOCUS, packageName).apply();
    }

    private void restoreLastFocus() {
        if (lastFocusPackage == null) {
            if (adapter.getItemCount() > 0) focusAppAtPosition(0);
            return;
        }
        for (int i = 0; i < appList.size(); i++) {
            if (appList.get(i).packageName.equals(lastFocusPackage)) {
                focusAppAtPosition(i);
                return;
            }
        }
        for (int i = 0; i < recentList.size(); i++) {
            if (recentList.get(i).packageName.equals(lastFocusPackage)) {
                focusRecentAtPosition(i);
                return;
            }
        }
        if (adapter.getItemCount() > 0) focusAppAtPosition(0);
    }

    private void focusAppAtPosition(int position) {
        appRecycler.scrollToPosition(position);
        appRecycler.post(() -> {
            RecyclerView.ViewHolder vh = appRecycler.findViewHolderForAdapterPosition(position);
            if (vh != null) vh.itemView.requestFocus();
        });
    }

    private void focusRecentAtPosition(int position) {
        recentRecycler.scrollToPosition(position);
        recentRecycler.post(() -> {
            RecyclerView.ViewHolder vh = recentRecycler.findViewHolderForAdapterPosition(position);
            if (vh != null) vh.itemView.requestFocus();
        });
    }

    private void showWallpaperMenu() {
        String[] options = {"从相册选择壁纸", "清除壁纸（恢复纯黑）", "调整遮罩浓度"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("壁纸设置")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: pickWallpaper(); break;
                        case 1: clearWallpaper(); break;
                        case 2: showScrimDialog(); break;
                    }
                }).show();
    }

    private void pickWallpaper() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择壁纸"), REQUEST_PICK_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开相册", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
                getSharedPreferences("launcher", MODE_PRIVATE).edit().putString(PREF_WALLPAPER, uri.toString()).apply();
                setWallpaper(uri);
                Toast.makeText(this, "壁纸已设置", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadWallpaper() {
        String uriStr = getSharedPreferences("launcher", MODE_PRIVATE).getString(PREF_WALLPAPER, null);
        if (uriStr != null) {
            try {
                setWallpaper(Uri.parse(uriStr));
            } catch (Exception e) {
                clearWallpaper();
            }
        }
    }

    private void setWallpaper(Uri uri) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, options);
            options.inSampleSize = calculateInSampleSize(options, 1920, 1080);
            options.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, options);
            if (bitmap != null) animateWallpaperChange(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "加载壁纸失败", e);
            Toast.makeText(this, "壁纸加载失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void animateWallpaperChange(final Bitmap newBitmap) {
        if (wallpaperView.getVisibility() == View.VISIBLE && wallpaperView.getDrawable() != null) {
            wallpaperView.animate().alpha(0f).scaleX(1.05f).scaleY(1.05f).setDuration(280)
                    .withEndAction(() -> applyNewWallpaper(newBitmap)).start();
            wallpaperScrim.animate().alpha(0.3f).setDuration(280).start();
        } else {
            applyNewWallpaper(newBitmap);
        }
    }

    private void applyNewWallpaper(Bitmap bitmap) {
        wallpaperView.setImageBitmap(bitmap);
        wallpaperView.setVisibility(View.VISIBLE);
        wallpaperView.setAlpha(0f);
        wallpaperView.setScaleX(1.08f);
        wallpaperView.setScaleY(1.08f);
        wallpaperScrim.setVisibility(View.VISIBLE);
        wallpaperScrim.setAlpha(0f);
        wallpaperView.animate().alpha(1f).scaleX(1.0f).scaleY(1.0f).setDuration(450)
                .setInterpolator(new OvershootInterpolator(0.8f)).start();
        wallpaperScrim.animate().alpha(1f).setDuration(400).start();
    }

    private void clearWallpaper() {
        getSharedPreferences("launcher", MODE_PRIVATE).edit().remove(PREF_WALLPAPER).apply();
        if (wallpaperView.getVisibility() != View.VISIBLE) {
            Toast.makeText(this, "当前没有壁纸", Toast.LENGTH_SHORT).show();
            return;
        }
        wallpaperView.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f).setDuration(350)
                .withEndAction(() -> {
                    wallpaperView.setImageDrawable(null);
                    wallpaperView.setVisibility(View.GONE);
                    wallpaperView.setScaleX(1f);
                    wallpaperView.setScaleY(1f);
                }).start();
        wallpaperScrim.animate().alpha(0f).setDuration(350)
                .withEndAction(() -> wallpaperScrim.setVisibility(View.GONE)).start();
        Toast.makeText(this, "已清除壁纸", Toast.LENGTH_SHORT).show();
    }

    private void showScrimDialog() {
        String[] levels = {"浅（更透）", "中（推荐）", "深（更暗）"};
        new android.app.AlertDialog.Builder(this).setTitle("遮罩浓度")
                .setItems(levels, (d, which) -> {
                    int alpha;
                    switch (which) {
                        case 0: alpha = 0x66; break;
                        case 1: alpha = 0x99; break;
                        default: alpha = 0xCC; break;
                    }
                    wallpaperScrim.setBackgroundColor(alpha << 24);
                    Toast.makeText(this, "遮罩已调整", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private void showShizukuMenu() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "请先启动 Shizuku", Toast.LENGTH_LONG).show();
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_CODE);
            return;
        }
        String[] options = {"强制设为默认桌面", "禁用系统原桌面", "恢复系统桌面"};
        new android.app.AlertDialog.Builder(this).setTitle("Shizuku 加强功能")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: forceSetAsHome(); break;
                        case 1: disableStockLauncher(); break;
                        case 2: enableStockLauncher(); break;
                    }
                }).show();
    }

    private void forceSetAsHome() {
        executeShell("cmd package set-home-activity " + getPackageName() + "/.MainActivity", "已尝试设为默认桌面");
    }

    private void disableStockLauncher() {
        String[] pkgs = {"com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx", "com.android.launcher3"};
        for (String pkg : pkgs) executeShell("pm disable-user --user 0 " + pkg, null);
        Toast.makeText(this, "已尝试禁用系统桌面", Toast.LENGTH_SHORT).show();
    }

    private void enableStockLauncher() {
        String[] pkgs = {"com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx", "com.android.launcher3"};
        for (String pkg : pkgs) executeShell("pm enable " + pkg, null);
        Toast.makeText(this, "已尝试恢复系统桌面", Toast.LENGTH_SHORT).show();
    }

    private void executeShell(String command, String successMsg) {
        new Thread(() -> {
            try {
                Process process = Shizuku.newProcess(new String[]{"sh", "-c", command}, null, null);
                process.waitFor();
                if (successMsg != null) runOnUiThread(() -> Toast.makeText(this, successMsg, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e(TAG, "Shell error", e);
                runOnUiThread(() -> Toast.makeText(this, "执行失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        View current = getCurrentFocus();
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
                showShizukuMenu();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (current != null && isInAppRecycler(current)) {
                    int pos = appRecycler.getChildAdapterPosition(current);
                    if (pos >= 0 && pos < 5 && recentAdapter.getItemCount() > 0) {
                        focusRecentAtPosition(Math.min(pos, recentAdapter.getItemCount() - 1));
                        return true;
                    }
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (current != null && isInRecentRecycler(current) && adapter.getItemCount() > 0) {
                    focusAppAtPosition(0);
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isInAppRecycler(View view) { return findRecyclerParent(view) == appRecycler; }
    private boolean isInRecentRecycler(View view) { return findRecyclerParent(view) == recentRecycler; }

    private RecyclerView findRecyclerParent(View view) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof RecyclerView) return (RecyclerView) parent;
            parent = parent.getParent();
        }
        return null;
    }

    @Override
    public void onBackPressed() {}

    static class AppInfo {
        String packageName;
        String name;
        Drawable icon;
    }

    class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppInfo app = appList.get(position);
            holder.tvName.setText(app.name);
            holder.ivIcon.setImageDrawable(app.icon);
            holder.itemView.setOnClickListener(v -> {
                try {
                    Intent intent = getPackageManager().getLaunchIntentForPackage(app.packageName);
                    if (intent != null) {
                        saveLastFocus(app.packageName);
                        addToRecent(app.packageName);
                        startActivity(intent);
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "无法打开应用", Toast.LENGTH_SHORT).show();
                }
            });
            holder.itemView.setOnLongClickListener(v -> { showShizukuMenu(); return true; });
            holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    saveLastFocus(app.packageName);
                    holder.focusBorder.setVisibility(View.VISIBLE);
                    v.animate().scaleX(1.13f).scaleY(1.13f).setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
                } else {
                    holder.focusBorder.setVisibility(View.GONE);
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
                }
            });
        }

        @Override
        public int getItemCount() { return appList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName;
            View focusBorder;
            ViewHolder(View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.ivIcon);
                tvName = itemView.findViewById(R.id.tvName);
                focusBorder = itemView.findViewById(R.id.focusBorder);
                itemView.setFocusable(true);
                itemView.setFocusableInTouchMode(true);
            }
        }
    }

    class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppInfo app = recentList.get(position);
            holder.tvName.setText(app.name);
            holder.ivIcon.setImageDrawable(app.icon);
            holder.itemView.setOnClickListener(v -> {
                try {
                    Intent intent = getPackageManager().getLaunchIntentForPackage(app.packageName);
                    if (intent != null) {
                        saveLastFocus(app.packageName);
                        addToRecent(app.packageName);
                        startActivity(intent);
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "无法打开", Toast.LENGTH_SHORT).show();
                }
            });
            holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    saveLastFocus(app.packageName);
                    holder.focusBorder.setVisibility(View.VISIBLE);
                    v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(150).start();
                } else {
                    holder.focusBorder.setVisibility(View.GONE);
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            });
        }

        @Override
        public int getItemCount() { return recentList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName;
            View focusBorder;
            ViewHolder(View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.ivIcon);
                tvName = itemView.findViewById(R.id.tvName);
                focusBorder = itemView.findViewById(R.id.focusBorder);
                itemView.setFocusable(true);
                itemView.setFocusableInTouchMode(true);
            }
        }
    }
}
