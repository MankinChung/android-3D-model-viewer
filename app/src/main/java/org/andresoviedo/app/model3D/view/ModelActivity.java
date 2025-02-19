package org.andresoviedo.app.model3D.view;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import org.andresoviedo.android_3d_model_engine.camera.CameraController;
import org.andresoviedo.android_3d_model_engine.collision.CollisionController;
import org.andresoviedo.android_3d_model_engine.collision.CollisionEvent;
import org.andresoviedo.android_3d_model_engine.controller.TouchController;
import org.andresoviedo.android_3d_model_engine.controller.TouchEvent;
import org.andresoviedo.android_3d_model_engine.event.SelectedObjectEvent;
import org.andresoviedo.android_3d_model_engine.model.Projection;
import org.andresoviedo.android_3d_model_engine.services.LoaderTask;
import org.andresoviedo.android_3d_model_engine.services.SceneLoader;
import org.andresoviedo.android_3d_model_engine.view.FPSEvent;
import org.andresoviedo.android_3d_model_engine.view.ModelSurfaceView;
import org.andresoviedo.android_3d_model_engine.view.ViewEvent;
import org.andresoviedo.app.model3D.demo.DemoLoaderTask;
import org.andresoviedo.dddmodel2.R;
import org.andresoviedo.util.android.ContentUtils;
import org.andresoviedo.util.event.EventListener;

import java.io.IOException;
import java.net.URI;
import java.util.EventObject;

/**
 * This activity represents the container for our 3D viewer.
 *
 * @author andresoviedo
 */
public class ModelActivity extends Activity implements EventListener {

    private static final int REQUEST_CODE_LOAD_TEXTURE = 1000;
    private static final int FULLSCREEN_DELAY = 10000;

    /**
     * Type of model if file name has no extension (provided though content provider)
     */
    private int paramType;
    /**
     * The file to load. Passed as input parameter
     */
    private URI paramUri;
    /**
     * Enter into Android Immersive mode so the renderer is full screen or not
     */
    private boolean immersiveMode;
    /**
     * Background GL clear color. Default is light gray
     */
    private float[] backgroundColor = new float[]{0.0f, 0.0f, 0.0f, 1.0f};

    private ModelSurfaceView glView;
    private TouchController touchController;
    private SceneLoader scene;
    private ModelViewerGUI gui;
    private CollisionController collisionController;


    private Handler handler;
    private CameraController cameraController;

    private SensorManager sensorManager;
    private Sensor sensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("ModelActivity", "onCreate: Loading activity... "+savedInstanceState);
        super.onCreate(savedInstanceState);

        // Try to get input parameters
        Bundle b = getIntent().getExtras();
        if (b != null) {
            try {
                if (b.getString("uri") != null) {
                    this.paramUri = new URI(b.getString("uri"));
                    Log.i("ModelActivity", "Params: uri '" + paramUri + "'");
                }
                this.paramType = b.getString("type") != null ? Integer.parseInt(b.getString("type")) : -1;
                this.immersiveMode = "true".equalsIgnoreCase(b.getString("immersiveMode"));

                if (b.getString("backgroundColor") != null) {
                    String[] backgroundColors = b.getString("backgroundColor").split(" ");
                    backgroundColor[0] = Float.parseFloat(backgroundColors[0]);
                    backgroundColor[1] = Float.parseFloat(backgroundColors[1]);
                    backgroundColor[2] = Float.parseFloat(backgroundColors[2]);
                    backgroundColor[3] = Float.parseFloat(backgroundColors[3]);
                }
            } catch (Exception ex) {
                Log.e("ModelActivity", "Error parsing activity parameters: " + ex.getMessage(), ex);
            }

        }

        handler = new Handler(getMainLooper());

        // Create our 3D scenario
        Log.i("ModelActivity", "Loading Scene...");
        scene = new SceneLoader(this, paramUri, paramType);
        scene.addListener(this);
        if (paramUri == null) {
            final LoaderTask task = new DemoLoaderTask(this, null, scene);
            task.execute();
        }

/*        Log.i("ModelActivity","Loading Scene...");
        if (paramUri == null) {
            scene = new ExampleSceneLoader(this);
        } else {
            scene = new SceneLoader(this, paramUri, paramType, gLView);
        }*/

        try {
            Log.i("ModelActivity", "Loading GLSurfaceView...");
            glView = new ModelSurfaceView(this, backgroundColor, this.scene);
            glView.addListener(this);
            setContentView(glView);
//            scene.setView(glView);
        } catch (Exception e) {
            Log.e("ModelActivity", e.getMessage(), e);
            Toast.makeText(this, "Error loading OpenGL view:\n" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        try {
            Log.i("ModelActivity", "Loading TouchController...");
            touchController = new TouchController(this);
            touchController.addListener(this);
            //touchController.addListener(glView);
        } catch (Exception e) {
            Log.e("ModelActivity", e.getMessage(), e);
            Toast.makeText(this, "Error loading TouchController:\n" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        try {
            Log.i("ModelActivity", "Loading CollisionController...");
            collisionController = new CollisionController(glView, scene);
            collisionController.addListener(this);
            //touchController.addListener(collisionController);
            //touchController.addListener(scene);
        } catch (Exception e) {
            Log.e("ModelActivity", e.getMessage(), e);
            Toast.makeText(this, "Error loading CollisionController\n" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        try {
            Log.i("ModelActivity", "Loading CameraController...");
            cameraController = new CameraController(scene.getCamera());
            //glView.getModelRenderer().addListener(cameraController);
            //touchController.addListener(cameraController);
        } catch (Exception e) {
            Log.e("ModelActivity", e.getMessage(), e);
            Toast.makeText(this, "Error loading CameraController" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        try {
            // TODO: finish UI implementation
            Log.i("ModelActivity", "Loading GUI...");
            gui = new ModelViewerGUI(glView, scene);
            touchController.addListener(gui);
            glView.addListener(gui);
            scene.addGUIObject(gui);
        } catch (Exception e) {
            Log.e("ModelActivity", e.getMessage(), e);
            Toast.makeText(this, "Error loading GUI" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // Show the Up button in the action bar.
        setupActionBar();

        setupOnSystemVisibilityChangeListener();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setupOrientationListener();
        }

        // load model
        scene.init();

        Log.i("ModelActivity", "Finished loading");
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void setupOrientationListener() {
        try {
            //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            //sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (sensor != null) {
                sensorManager.registerListener(new SensorEventListener() {
                                                   @Override
                                                   public void onSensorChanged(SensorEvent event) {
                                                       /*Log.v("ModelActivity","sensor: "+ Arrays.toString(event.values));
                                                           Quaternion orientation = new Quaternion(event.values);
                                                           orientation.normalize();
                                                           //scene.getSelectedObject().setOrientation(orientation);
                                                           glView.setOrientation(orientation);*/
                                                   }

                                                   @Override
                                                   public void onAccuracyChanged(Sensor sensor, int accuracy) {

                                                   }
                                               }, sensor,
                        SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
            }
            OrientationEventListener mOrientationListener = new OrientationEventListener(
                    getApplicationContext()) {
                @Override
                public void onOrientationChanged(int orientation) {
                    //scene.onOrientationChanged(orientation);
                }
            };

            if (mOrientationListener.canDetectOrientation()) {
                mOrientationListener.enable();
            }
        } catch (Exception e) {
            Log.e("ModelActivity","There is an issue setting up sensors",e);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putFloatArray("camera.pos",scene.getCamera().getPos());
        outState.putFloatArray("camera.view",scene.getCamera().getView());
        outState.putFloatArray("camera.up",scene.getCamera().getUp());
        outState.putString("renderer.projection",glView.getProjection().name());
        outState.putInt("renderer.skybox",glView.getSkyBoxId());
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        if(state.containsKey("renderer.projection")) {
            glView.setProjection(Projection.valueOf(state.getString("renderer.projection")));
        }
        if(state.containsKey("camera.pos") && state.containsKey("camera.view") && state.containsKey("camera.up")){
            Log.d("ModelActivity","onRestoreInstanceState: Restoring camera settings...");
            scene.getCamera().set(
                    state.getFloatArray("camera.pos"),
                    state.getFloatArray("camera.view"),
                    state.getFloatArray("camera.up"));
        }
        if(state.containsKey("renderer.skybox")){
            glView.setSkyBox(state.getInt("renderer.skybox"));
        }
    }


    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setupActionBar() {
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
        // getActionBar().setDisplayHomeAsUpEnabled(true);
        // }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.model, menu);
        return true;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setupOnSystemVisibilityChangeListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
            // Note that system bars will only be "visible" if none of the
            // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                // The system bars are visible. Make any desired
                hideSystemUIDelayed();
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUIDelayed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.model_toggle_projection:
                glView.toggleProjection();
                break;
            case R.id.model_toggle_wireframe:
                scene.toggleWireframe();
                break;
            case R.id.model_toggle_boundingbox:
                scene.toggleBoundingBox();
                break;
            case R.id.model_toggle_skybox:
                glView.toggleSkyBox();
                break;
            case R.id.model_toggle_textures:
                scene.toggleTextures();
                break;
            case R.id.model_toggle_animation:
                scene.toggleAnimation();
                break;
            case R.id.model_toggle_smooth:
                scene.toggleSmooth();
                break;
            case R.id.model_toggle_collision:
                scene.toggleCollision();
                break;
            case R.id.model_toggle_lights:
                scene.toggleLighting();
                break;
            case R.id.model_toggle_stereoscopic:
                scene.toggleStereoscopic();
                break;
            case R.id.model_toggle_blending:
                scene.toggleBlending();
                break;
            case R.id.model_toggle_immersive:
                toggleImmersive();
                break;
            case R.id.model_load_texture:
                Intent target = ContentUtils.createGetContentIntent("image/*");
                Intent intent = Intent.createChooser(target, "Select a file");
                try {
                    startActivityForResult(intent, REQUEST_CODE_LOAD_TEXTURE);
                } catch (ActivityNotFoundException e) {
                    // The reason for the existence of aFileChooser
                }
                break;
        }

        hideSystemUIDelayed();
        return super.onOptionsItemSelected(item);
    }

    private void toggleImmersive() {
        this.immersiveMode = !this.immersiveMode;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }
        if (this.immersiveMode) {
            hideSystemUI();
        } else {
            showSystemUI();
        }
        Toast.makeText(this, "Fullscreen " + this.immersiveMode, Toast.LENGTH_SHORT).show();
    }

    private void hideSystemUIDelayed() {
        if (!this.immersiveMode) {
            return;
        }
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::hideSystemUI, FULLSCREEN_DELAY);

    }

    private void hideSystemUI() {
        if (!this.immersiveMode) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            hideSystemUIKitKat();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            hideSystemUIJellyBean();
        }
    }

    // This snippet hides the system bars.
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void hideSystemUIKitKat() {
        // Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                | View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void hideSystemUIJellyBean() {
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }

    // This snippet shows the system bars. It does this by removing all the flags
    // except for the ones that make the content appear under the system bars.
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void showSystemUI() {
        handler.removeCallbacksAndMessages(null);
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case REQUEST_CODE_LOAD_TEXTURE:
                // The URI of the selected file
                final Uri uri = data.getData();
                if (uri != null) {
                    Log.i("ModelActivity", "Loading texture '" + uri + "'");
                    try {
                        ContentUtils.setThreadActivity(this);
                        scene.loadTexture(null, uri);
                    } catch (IOException ex) {
                        Log.e("ModelActivity", "Error loading texture: " + ex.getMessage(), ex);
                        Toast.makeText(this, "Error loading texture '" + uri + "'. " + ex
                                .getMessage(), Toast.LENGTH_LONG).show();
                    } finally {
                        ContentUtils.setThreadActivity(null);
                    }
                }
        }
    }

    @Override
    public boolean onEvent(EventObject event) {
        if (event instanceof FPSEvent){
            gui.onEvent(event);
        }
        else if (event instanceof SelectedObjectEvent){
            gui.onEvent(event);
        }
        else if (event.getSource() instanceof MotionEvent){
            // event coming from glview
            touchController.onMotionEvent((MotionEvent) event.getSource());
        }
        else if (event instanceof CollisionEvent){
            scene.onEvent(event);
        }
        else if (event instanceof TouchEvent){
            TouchEvent touchEvent = (TouchEvent) event;
            if (touchEvent.getAction() == TouchEvent.Action.CLICK){
                if (!collisionController.onEvent(event)){
                    scene.onEvent(event);
                }
            } else {
                if (scene.getSelectedObject() != null) {
                    scene.onEvent(event);
                } else {
                    cameraController.onEvent(event);
                    scene.onEvent(event);
                    if (((TouchEvent) event).getAction() == TouchEvent.Action.PINCH) {
                        glView.onEvent(event);
                    }
                }
            }
        }
        else if (event instanceof ViewEvent) {
            ViewEvent viewEvent = (ViewEvent) event;
            if (viewEvent.getCode() == ViewEvent.Code.SURFACE_CHANGED) {
                cameraController.onEvent(viewEvent);
                touchController.onEvent(viewEvent);

                // process event in GUI
                if (gui != null) {
                    gui.setSize(viewEvent.getWidth(), viewEvent.getHeight());
                    gui.setVisible(true);
                }
            } else if (viewEvent.getCode() == ViewEvent.Code.PROJECTION_CHANGED){
                cameraController.onEvent(event);
            }
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (immersiveMode) {
            toggleImmersive();
        } else {
            super.onBackPressed();
        }
    }
}
