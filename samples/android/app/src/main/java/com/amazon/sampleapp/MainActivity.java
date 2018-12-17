/*
 * Copyright 2017-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.sampleapp;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.amazon.aace.alexa.AlexaClient;
import com.amazon.aace.alexa.AlexaProperties;
import com.amazon.aace.alexa.Speaker;
import com.amazon.aace.alexa.config.AlexaConfiguration;
import com.amazon.aace.carControl.CarControlConfiguration;
import com.amazon.aace.carControl.ClimateControlInterface;
import com.amazon.aace.communication.config.AlexaCommsConfiguration;
import com.amazon.aace.core.CoreProperties;
import com.amazon.aace.core.Engine;
import com.amazon.aace.core.config.EngineConfiguration;
import com.amazon.aace.localSkillService.config.LocalSkillServiceConfiguration;
import com.amazon.aace.localVoiceControl.config.LocalVoiceControlConfiguration;
import com.amazon.aace.logger.Logger;
import com.amazon.aace.logger.config.LoggerConfiguration;
import com.amazon.aace.modules.ExtraModules;
import com.amazon.aace.storage.config.StorageConfiguration;
import com.amazon.aace.vehicle.config.VehicleConfiguration;
/* Sample Metrics Code */
//import com.amazon.metricuploadservice.MetricsUploadService;
//import com.amazon.metricuploadservice.MetricUploadConfiguration;
import com.amazon.sampleapp.impl.Alerts.AlertsHandler;
import com.amazon.sampleapp.impl.AlexaClient.AlexaClientHandler;
import com.amazon.sampleapp.impl.AudioPlayer.AudioPlayerHandler;
import com.amazon.sampleapp.impl.AuthProvider.AuthProviderHandler;
import com.amazon.sampleapp.impl.CarControl.ClimateControlHandler;
import com.amazon.sampleapp.impl.Common.AudioInputManager;
import com.amazon.sampleapp.impl.Communication.AlexaCommsHandler;
import com.amazon.sampleapp.impl.Communication.AlexaCommsView;
import com.amazon.sampleapp.impl.ContactIngestion.ContactUploader.ContactUploaderHandler;
import com.amazon.sampleapp.impl.ExternalMediaPlayer.MACCPlayer;
import com.amazon.sampleapp.impl.LocalMediaSource.CDLocalMediaSource;
import com.amazon.sampleapp.impl.LocationProvider.LocationProviderHandler;
import com.amazon.sampleapp.impl.Logger.LoggerHandler;
import com.amazon.sampleapp.impl.MediaPlayer.MediaPlayerHandler;
import com.amazon.sampleapp.impl.MediaPlayer.RawAudioMediaPlayerHandler;
import com.amazon.sampleapp.impl.Navigation.NavigationHandler;
import com.amazon.sampleapp.impl.NetworkInfoProvider.NetworkInfoProviderHandler;
import com.amazon.sampleapp.impl.Notifications.NotificationsHandler;
import com.amazon.sampleapp.impl.PhoneCallController.PhoneCallControllerHandler;
import com.amazon.sampleapp.impl.PlaybackController.PlaybackControllerHandler;
import com.amazon.sampleapp.impl.SpeechRecognizer.SpeechRecognizerHandler;
import com.amazon.sampleapp.impl.SpeechSynthesizer.SpeechSynthesizerHandler;
import com.amazon.sampleapp.impl.TemplateRuntime.TemplateRuntimeHandler;
import com.amazon.sampleapp.logView.LogEntry;
import com.amazon.sampleapp.logView.LogRecyclerViewAdapter;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

public class MainActivity extends AppCompatActivity implements Observer {

    // Max number of logs to be kept in memory
    private static final int MAX_NUM_LOGS = 2000;

    /* AACE Platform Interface Handlers */

    // Common
    private AudioInputManager mAudioInputManager;

    // Alexa
    private AlertsHandler mAlerts;
    private AlexaClientHandler mAlexaClient;
    private AudioPlayerHandler mAudioPlayer;
    private AuthProviderHandler mAuthProvider;
    private AlexaCommsHandler mAlexaCommsHandler;
    private NotificationsHandler mNotifications;
    private PhoneCallControllerHandler mPhoneCallController;
    private ContactUploaderHandler mContactUploader;
    private PlaybackControllerHandler mPlaybackController;
    private SpeechRecognizerHandler mSpeechRecognizer;
    private SpeechSynthesizerHandler mSpeechSynthesizer;
    private TemplateRuntimeHandler mTemplateRuntime;

    // Core
    private Engine mEngine;

    // Location
    private LocationProviderHandler mLocationProvider;

    // Logger
    private LoggerHandler mLogger;

    // Navigation
    private NavigationHandler mNavigation;

    // Network
    private NetworkInfoProviderHandler mNetworkInfoProvider;

    // Climate control
    private ClimateControlHandler mClimateControl;

    /* Log View Components */
    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mRecyclerAdapter;
    private LimitedSizeArrayList<LogEntry> mLogList = new LimitedSizeArrayList<>( MAX_NUM_LOGS );

    /* Shared Preferences */
    private SharedPreferences mPreferences;

    /* Speech Recognition Components */
    private MediaPlayer mAudioCueStartVoice; // Voice-initiated listening audio cue
    private MediaPlayer mAudioCueStartTouch; // Touch-initiated listening audio cue
    private MediaPlayer mAudioCueEnd; // End of listening audio cue
    private boolean mIsTalkButtonLongPressed = false;

    private static final String sTag = "CLI";
    private static final String sDeviceConfigFile = "app_config.json";
    private static final int sPermissionRequestCode = 0;
    private static final String[] sRequiredPermissions = { Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION };
    private MACCPlayer mMACCPlayer;

    private CDLocalMediaSource mCDLocalMediaSource;

    /* Sample Metrics Code */
    //    private MetricsUploadService mMetricsUploadService;

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );

        // Check if permissions are missing and must be requested
        ArrayList<String> requests = new ArrayList<>();

        for ( String permission : sRequiredPermissions) {
            if ( ActivityCompat.checkSelfPermission( this, permission )
                    == PackageManager.PERMISSION_DENIED ) {
                requests.add( permission );
            }
        }

        // Request necessary permissions if not already granted, else start app
        if ( requests.size() > 0 ) {
            ActivityCompat.requestPermissions( this,
                    requests.toArray( new String[requests.size()] ), sPermissionRequestCode );
        } else create();
    }

    @Override
    public void onRequestPermissionsResult( int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults ) {
        if ( requestCode == sPermissionRequestCode) {
            if ( grantResults.length > 0 ) {
                for ( int grantResult : grantResults ) {
                    if ( grantResult == PackageManager.PERMISSION_DENIED ) {
                        // Permission request was denied
                        Toast.makeText( this, "Permissions required",
                                Toast.LENGTH_LONG ).show();
                    }
                }
                // Permissions have been granted. Start app
                create();
            } else {
                // Permission request was denied
                Toast.makeText( this, "Permissions required", Toast.LENGTH_LONG ).show();
            }
        }
    }

    private void create() {

        // Set the main view content
        setContentView( R.layout.activity_main );

        // Add support action toolbar for action buttons
        setSupportActionBar( ( Toolbar ) findViewById( R.id.actionToolbar ) );

        // Get shared preferences
        mPreferences = getSharedPreferences( getString( R.string.preference_file_key ),
                Context.MODE_PRIVATE );

        // Initialize RecyclerView list for log view
        mRecyclerView = findViewById( R.id.rvLog );
        mRecyclerView.setHasFixedSize( true );
        mRecyclerView.setLayoutManager( new LinearLayoutManager( this ) );
        mRecyclerAdapter = new LogRecyclerViewAdapter( mLogList, getApplicationContext() );
        mRecyclerView.setAdapter( mRecyclerAdapter );

        // Initialize sound effects for speech recognition
        mAudioCueStartVoice = MediaPlayer.create( this, R.raw.med_ui_wakesound );
        mAudioCueStartTouch = MediaPlayer.create( this, R.raw.med_ui_wakesound_touch );
        mAudioCueEnd = MediaPlayer.create( this, R.raw.med_ui_endpointing_touch );

        // Retrieve device config from config file and update preferences
        String clientId = "", clientSecret = "", productId = "", productDsn = "";
        JSONObject config = getConfigFromFile("config");
        if ( config != null ) {
            try {
                clientId = config.getString( "clientId" );
                clientSecret = config.getString( "clientSecret" );
                productId = config.getString( "productId" );
                productDsn = config.getString( "productDsn" );
            } catch ( JSONException e ) {
                Log.w( sTag, "Missing device info in app_config.json" );
            }
        }
        updateDevicePreferences( clientId, clientSecret, productId, productDsn );

        // Display device config settings in GUI
        updateDeviceConfigGUI( clientId, clientSecret, productId, productDsn );

        // Initialize AAC engine and register platform interfaces
        try {
            startEngine();
        } catch ( RuntimeException e ) {
            Log.e( sTag, "Could not start engine. Reason: " + e.getMessage() );
            return;
        }

        // Observe log event changes to update the log view
        mLogger.addLogObserver( this );
        mSpeechRecognizer.addObserver( this );
    }

    private void startEngine() throws RuntimeException {

        // Copy certs to the cache directory
        File cacheDir = getCacheDir();
        File appDataDir = new File( cacheDir, "appdata" );
        File certsDir = new File( appDataDir, "certs" );
        try {
            String[] certAssets = getAssets().list("certs");
            for ( String next : certAssets ) {
                copyAsset("certs/" + next, new File( certsDir, next ), false);
            }
        } catch ( IOException e ) {
            Log.w( sTag, "Cannot copy certs to cache directory. Error: " + e.getMessage() );
        }

        // Create AAC engine
        mEngine = Engine.create();

        // Configure the engine
        String productDsn = mPreferences.getString( getString( R.string.preference_product_dsn ), "" );
        String clientId = mPreferences.getString( getString( R.string.preference_client_id ), "" );
        String productId = mPreferences.getString( getString( R.string.preference_product_id ), "" );

        AlexaConfiguration.TemplateRuntimeTimeout [] timeoutList = new AlexaConfiguration.TemplateRuntimeTimeout[ ]{
                new AlexaConfiguration.TemplateRuntimeTimeout ( AlexaConfiguration.TemplateRuntimeTimeoutType.DISPLAY_CARD_TTS_FINISHED_TIMEOUT, 8000 ),
                new AlexaConfiguration.TemplateRuntimeTimeout ( AlexaConfiguration.TemplateRuntimeTimeoutType.DISPLAY_CARD_AUDIO_PLAYBACK_FINISHED_TIMEOUT, 8000),
                new AlexaConfiguration.TemplateRuntimeTimeout ( AlexaConfiguration.TemplateRuntimeTimeoutType.DISPLAY_CARD_AUDIO_PLAYBACK_STOPPED_PAUSED_TIMEOUT, 1800000)
        };


        boolean configureSucceeded = mEngine.configure( new EngineConfiguration[]{
                //AlexaConfiguration.createCurlConfig( certsDir.getPath(), "wlan0" ), Uncomment this line to specify the interface name to use by AVS.
                AlexaConfiguration.createCurlConfig( certsDir.getPath() ),
                AlexaConfiguration.createDeviceInfoConfig(  productDsn, clientId, productId ),
                AlexaConfiguration.createMiscStorageConfig( appDataDir.getPath() + "/miscStorage.sqlite" ),
                AlexaConfiguration.createCertifiedSenderConfig( appDataDir.getPath() + "/certifiedSender.sqlite" ),
                AlexaConfiguration.createAlertsConfig( appDataDir.getPath() + "/alerts.sqlite" ),
                AlexaConfiguration.createSettingsConfig( appDataDir.getPath() + "/settings.sqlite" ),
                AlexaConfiguration.createNotificationsConfig( appDataDir.getPath() + "/notifications.sqlite" ),
                StorageConfiguration.createLocalStorageConfig( appDataDir.getPath() + "/localStorage.sqlite" ),
                LoggerConfiguration.createSyslogSinkConfig( "syslog", Logger.Level.VERBOSE ),
                AlexaCommsConfiguration.createCommsConfig( certsDir.getPath() ),
                //AlexaConfiguration.createTemplateRuntimeTimeoutConfig( timeoutList )
                // Example Vehicle Config
                VehicleConfiguration.createVehicleInfoConfig( new VehicleConfiguration.VehicleProperty[]{
                        new VehicleConfiguration.VehicleProperty(VehicleConfiguration.VehiclePropertyType.MAKE, "Amazon"),
                        new VehicleConfiguration.VehicleProperty(VehicleConfiguration.VehiclePropertyType.MODEL, "AmazonCarOne"),
                        new VehicleConfiguration.VehicleProperty(VehicleConfiguration.VehiclePropertyType.TRIM, "Advance"),
                        new VehicleConfiguration.VehicleProperty(VehicleConfiguration.VehiclePropertyType.YEAR, "2025"),
                        new VehicleConfiguration.VehicleProperty(VehicleConfiguration.VehiclePropertyType.GEOGRAPHY, "US"),
                        new VehicleConfiguration.VehicleProperty(VehicleConfiguration.VehiclePropertyType.VERSION, String.format(
                            "Vehicle Software Version 1.0 (Auto SDK Version %s)", mEngine.getProperty( CoreProperties.VERSION ) ) ),
                        new VehicleConfiguration.VehicleProperty(VehicleConfiguration.VehiclePropertyType.OPERATING_SYSTEM, "Android 8.1 Oreo API Level 26"),
                        new VehicleConfiguration.VehicleProperty(VehicleConfiguration.VehiclePropertyType.HARDWARE_ARCH, "Armv8a"),
                        new VehicleConfiguration.VehicleProperty(VehicleConfiguration.VehiclePropertyType.LANGUAGE, "en-US"),
                        new VehicleConfiguration.VehicleProperty(VehicleConfiguration.VehiclePropertyType.MICROPHONE, "Single, roof mounted"),
                        // If this list is left blank, it will be fetched by the engine using amazon default endpoint
                        new VehicleConfiguration.VehicleProperty(VehicleConfiguration.VehiclePropertyType.COUNTRY_LIST, "US,GB,IE,CA,DE,AT,IN,JP,AU,NZ,FR")
                }),
                LocalVoiceControlConfiguration.createIPCConfig( appDataDir.getPath(), LocalVoiceControlConfiguration.SocketPermission.ALL, appDataDir.getPath(), LocalVoiceControlConfiguration.SocketPermission.ALL, "127.0.0.1", appDataDir.getPath() ),
                //AlexaConfiguration.createTemplateRuntimeTimeoutConfig( timeoutList ),
                LocalSkillServiceConfiguration.createLocalSkillServiceConfig( appDataDir.getPath() + "/LSS.socket", appDataDir.getPath() + "/ER.socket" ),
                CarControlConfiguration.createCarControlConfig( appDataDir.getPath() + "/ApplianceDB.sqlite" )
        });
        if ( !configureSucceeded ) throw new RuntimeException( "Engine configuration failed" );

        // Create the platform implementation handlers and register them with the engine
        // Logger
        if ( !mEngine.registerPlatformInterface(
                mLogger = new LoggerHandler(this, ( LogRecyclerViewAdapter ) mRecyclerAdapter )
            )
        ) throw new RuntimeException( "Could not register Logger platform interface" );

        mAudioInputManager = new AudioInputManager(mLogger);

        // LocationProvider
        if ( !mEngine.registerPlatformInterface(
                mLocationProvider = new LocationProviderHandler( this, mLogger )
            )
        ) throw new RuntimeException( "Could not register LocationProvider platform interface" );

        // AlexaClient
        if ( !mEngine.registerPlatformInterface(
                mAlexaClient = new AlexaClientHandler( this, mLogger )
            )
        ) throw new RuntimeException( "Could not register AlexaClient platform interface" );

        // PhoneCallController
        if ( !mEngine.registerPlatformInterface(
                mPhoneCallController = new PhoneCallControllerHandler( this, mLogger )
            )
        ) throw new RuntimeException( "Could not register PhoneCallController platform interface" );

        // PlaybackController
        if ( !mEngine.registerPlatformInterface(
                mPlaybackController = new PlaybackControllerHandler( this, mLogger )
            )
        ) throw new RuntimeException( "Could not register PlaybackController platform interface" );

        // SpeechRecognizer
        boolean wakeWordSupported =
                mEngine.getProperty( AlexaProperties.WAKEWORD_SUPPORTED ).equals( "true" );
        if ( !mEngine.registerPlatformInterface(
                mSpeechRecognizer = new SpeechRecognizerHandler(
                        mAudioInputManager,
                        this,
                        mLogger,
                        wakeWordSupported,
                        true
                )
            )
        ) throw new RuntimeException( "Could not register SpeechRecognizer platform interface" );

        // AudioPlayer
        if ( !mEngine.registerPlatformInterface(
                mAudioPlayer = new AudioPlayerHandler(
                        new MediaPlayerHandler(
                                this,
                                mLogger, "Audio Player",
                                Speaker.Type.AVS_SPEAKER,
                                mPlaybackController )
                )
            )
        ) throw new RuntimeException( "Could not register AudioPlayer platform interface" );


        // SpeechSynthesizer
        if ( !mEngine.registerPlatformInterface(
                mSpeechSynthesizer = new SpeechSynthesizerHandler(
                        new MediaPlayerHandler(
                                this,
                                mLogger,
                                "Speech Synthesizer",
                                Speaker.Type.AVS_SPEAKER,
                                null )
                )
            )
        ) throw new RuntimeException( "Could not register SpeechSynthesizer platform interface" );

        // TemplateRuntime
        if ( !mEngine.registerPlatformInterface(
                mTemplateRuntime = new TemplateRuntimeHandler( mLogger, mPlaybackController )
            )
        ) throw new RuntimeException( "Could not register TemplateRuntime platform interface" );

        // Alerts
        if ( !mEngine.registerPlatformInterface(
                mAlerts = new AlertsHandler(
                        this,
                        mLogger,
                        new MediaPlayerHandler(
                                this,
                                mLogger,
                                "Alerts",
                                Speaker.Type.AVS_ALERTS,
                                null
                        )
                )
            )
        ) throw new RuntimeException( "Could not register Alerts platform interface" );

        // NetworkInfoProvider
        if ( !mEngine.registerPlatformInterface(
                mNetworkInfoProvider = new NetworkInfoProviderHandler( this, mLogger )
            )
        ) throw new RuntimeException( "Could not register NetworkInfoProvider platform interface" );

        // AuthProvider
        if ( !mEngine.registerPlatformInterface(
                mAuthProvider = new AuthProviderHandler( this, mLogger, mNetworkInfoProvider )
            )
        ) throw new RuntimeException( "Could not register AuthProvider platform interface" );

        mNetworkInfoProvider.setAuthProvider(mAuthProvider);

        // Navigation
        if ( !mEngine.registerPlatformInterface(
                mNavigation = new NavigationHandler( mLogger )
            )
        ) throw new RuntimeException( "Could not register Navigation platform interface" );

        // Notifications
        if ( !mEngine.registerPlatformInterface(
                mNotifications = new NotificationsHandler(
                        this,
                        mLogger,
                        new MediaPlayerHandler(
                                this,
                                mLogger,
                                "Notifications",
                                Speaker.Type.AVS_ALERTS,
                                null
                        )
                )
            )
        ) throw new RuntimeException( "Could not register Notifications platform interface" );

        // Contact-Uploader
        if (!mEngine.registerPlatformInterface(
            mContactUploader = new ContactUploaderHandler(this, mLogger)
            )
        ) throw new RuntimeException("Could not register ContactUploader platform interface");

        ExtraModules extraModules = new ExtraModules(getApplicationContext());
        // Alexa Comms Handler
        if (extraModules.isAlexaCommsEnabled()) {
            MediaPlayerHandler ringtoneMediaPlayerHandler = new MediaPlayerHandler(
                    this,
                    mLogger,
                    "CommunicationRingtone",
                    Speaker.Type.AVS_SPEAKER,
                    null);
            // Call audio player is required to play PCM 16 raw data @ 16 KHZ.
            // RawAudioMediaPlayerHandler can play from such a data stream.
            RawAudioMediaPlayerHandler callAudioMediaPlayerHandler = new RawAudioMediaPlayerHandler(
                    this,
                    mLogger,
                    "CommunicationCallAudio",
                    Speaker.Type.AVS_SPEAKER);
            if (!mEngine.registerPlatformInterface(
                    mAlexaCommsHandler = new AlexaCommsHandler(
                            mAudioInputManager,
                            ringtoneMediaPlayerHandler,
                            ringtoneMediaPlayerHandler.getSpeaker(),
                            callAudioMediaPlayerHandler,
                            callAudioMediaPlayerHandler.getSpeaker()
                    )
                )
            ) throw new RuntimeException("Could not register AlexaCommsHandler platform interface");
            AlexaCommsView commsView = findViewById(R.id.alexa_comms);
            commsView.setupUI(mAlexaCommsHandler);
        }

        // Climate Control Handler
        if (extraModules.isLocalVoiceControlEnabled()) {
            if (!mEngine.registerPlatformInterface(
                    mClimateControl = new ClimateControlHandler( this, mLogger )
                )
            ) throw new RuntimeException( "Could not register ClimateControl platform interface" );
            mClimateControl.addClimateControlSwitch();
            mClimateControl.addAirConditioningSwitch();
            ClimateControlInterface.AirConditioningMode[] modes = { ClimateControlInterface.AirConditioningMode.MANUAL, ClimateControlInterface.AirConditioningMode.AUTO };
            mClimateControl.addAirConditioningModeSelector( modes );
            mClimateControl.addFanSwitch( ClimateControlInterface.FanZone.ALL );
            mClimateControl.addFanSpeedControl( ClimateControlInterface.FanZone.ALL, 0, 100, 1 );
            mClimateControl.addTemperatureControl( ClimateControlInterface.TemperatureZone.ALL, 60, 80, 1, ClimateControlInterface.TemperatureUnit.FAHRENHEIT );
        }

        mMACCPlayer = new MACCPlayer(this,  mPlaybackController);
        if ( !mEngine.registerPlatformInterface( mMACCPlayer ) )  {
            Log.i("MACC", "registered not fine");
            throw new RuntimeException( "Could not register Notifications platform interface" );
        } else {
            Log.i("MACC", "registered fine");
        }
        mMACCPlayer.runDiscovery();

        // Mock CD platform handler
        if ( !mEngine.registerPlatformInterface(
                mCDLocalMediaSource = new CDLocalMediaSource(this, mLogger, CDLocalMediaSource.Source.COMPACT_DISC )
        ) ) throw new RuntimeException( "Could not register Mock CD player Local Media Source platform interface" );

        /* Sample Metrics Code */
//        // Create and configure MetricsUploadService
//        MetricUploadConfiguration metricUploadConfiguration = initializeMetricsConfiguration();
//        mMetricsUploadService = new MetricsUploadService( this.getApplicationContext(), metricUploadConfiguration );
//        if ( !mEngine.registerPlatformInterface( mMetricsUploadService) ) {
//            throw new RuntimeException( "Could not register MetricsUploader platform interface" );
//        }

        // Start the engine
        if ( !mEngine.start() ) throw new RuntimeException( "Could not start engine" );
        //log whether LocationProvider gave a supported country
        mLogger.postInfo( "Country Supported: ",  mEngine.getProperty( AlexaProperties.COUNTRY_SUPPORTED ));
        mAuthProvider.onInitialize();
        mContactUploader.onInitialize();
    }

    @Override
    public void onDestroy() {
        if ( mLogger != null ) mLogger.postInfo( sTag, "Engine stopped" );
        else Log.i( sTag, "Engine stopped" );

        if ( mAudioCueStartVoice != null ) {
            mAudioCueStartVoice.release();
            mAudioCueStartVoice = null;
        }
        if ( mAudioCueStartTouch != null ) {
            mAudioCueStartTouch.release();
            mAudioCueStartTouch = null;
        }
        if ( mAudioCueEnd != null ) {
            mAudioCueEnd.release();
            mAudioCueEnd = null;
        }

        if ( mNetworkInfoProvider != null ) { mNetworkInfoProvider.unregister(); }

        /* Sample Metrics Code */
//        if ( mMetricsUploadService != null ) { mMetricsUploadService.shutdown(); }

        if ( mEngine != null ) { mEngine.dispose(); }

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if ( mAuthProvider != null ) mAuthProvider.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        super.onCreateOptionsMenu( menu );
        getMenuInflater().inflate( R.menu.menu_main, menu );

        // Set tap-to-talk and hold-to-talk actions
        final MenuItem icon = menu.findItem( R.id.action_talk );
        if ( icon != null && mAlexaClient != null && mSpeechRecognizer != null ) {
            icon.setActionView( R.layout.menu_item_talk );

            // Set hold-to-talk action
            icon.getActionView().setOnClickListener( new View.OnClickListener() {
                @Override
                public void onClick( View v ) {
                    if ( mAlexaClient.getConnectionStatus()
                            == AlexaClient.ConnectionStatus.CONNECTED ) {
                        mSpeechRecognizer.onTapToTalk();
                    } else {
                        String message = "AlexaClient not connected. ConnectionStatus: "
                                + mAlexaClient.getConnectionStatus();
                        if ( mLogger != null )  mLogger.postWarn( sTag, message );
                        else Log.w( sTag, message );
                    }
                }
            });

            // Start hold-to-talk button action
            icon.getActionView().setOnLongClickListener( new View.OnLongClickListener() {
                @Override
                public boolean onLongClick( View v ) {
                    if ( mAlexaClient.getConnectionStatus()
                            == AlexaClient.ConnectionStatus.CONNECTED ) {
                        mIsTalkButtonLongPressed = true;
                        mSpeechRecognizer.onHoldToTalk();
                    } else {
                        if ( mLogger != null ) {
                            mLogger.postWarn( sTag,
                                    "ConnectionStatus: DISCONNECTED" );
                        } else Log.w( sTag, "ConnectionStatus: DISCONNECTED" );
                    }
                    return true;
                }
            });

            // Release hold-to-talk button action
            icon.getActionView().setOnTouchListener( new View.OnTouchListener() {
                @Override
                public boolean onTouch( View v, MotionEvent m ) {
                    // Talk button released
                    if ( m.getAction() == MotionEvent.ACTION_UP ) {
                        if ( mIsTalkButtonLongPressed ) {
                            mIsTalkButtonLongPressed = false;
                            mSpeechRecognizer.onReleaseHoldToTalk();
                        }
                    }
                    return false;
                }
            });
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item ) {
        switch ( item.getItemId() ) {
            case R.id.action_drawer:
                toggleDrawer();
                return true;
            default:
                return super.onOptionsItemSelected( item );
        }
    }

    public void toggleDrawer() {
        final View drawer = findViewById( R.id.drawer );
        if ( drawer.getVisibility() == View.VISIBLE ) {
            drawer.setVisibility( View.GONE );
        } else {
            drawer.setVisibility( View.VISIBLE );
        }
    }

    // For updating log view
    @Override
    public void update( Observable observable, Object object ) {

        if ( object instanceof LogEntry ) {
            // Append entry to log list
            final LogEntry entry = ( LogEntry ) object;

            runOnUiThread( new Runnable() {
                @Override
                public void run() {
                    // Insert entry into RecyclerView and update view
                    mLogList.add( entry );
                    mRecyclerAdapter.notifyItemInserted( mLogList.size() - 1 );
                    ( ( LogRecyclerViewAdapter ) mRecyclerAdapter ).filter();
                    int count = mRecyclerAdapter.getItemCount();
                    int position = count > 0 ? count - 1 : 0;
                    mRecyclerView.smoothScrollToPosition( position );
                }
            });

        } else if ( observable instanceof SpeechRecognizerHandler.AudioCueObservable ) {
            if ( object.equals( SpeechRecognizerHandler.AudioCueState.START_TOUCH ) ) {
                // Play touch-initiated listening audio cue
                mAudioCueStartTouch.start();
            } else if ( object.equals( SpeechRecognizerHandler.AudioCueState.START_VOICE ) ) {
                // Play voice-initiated listening audio cue
                mAudioCueStartVoice.start();
            } else if ( object.equals( SpeechRecognizerHandler.AudioCueState.END ) ) {
                // Play stop listening audio cue
                mAudioCueEnd.start();
            }
        }
    }

    private void copyAsset( String assetPath, File destFile, boolean force ) {
        if ( !destFile.exists() || force ) {
            if ( destFile.getParentFile().exists() || destFile.getParentFile().mkdirs() ) {
                // Copy the asset to the dest path
                try (
                    InputStream is = getAssets().open( assetPath );
                    OutputStream os = new FileOutputStream( destFile )
                ) {
                    byte[] buf = new byte[ 1024 ];
                    int len;
                    while ( ( len = is.read( buf ) ) > 0 ) {
                        os.write( buf, 0, len );
                    }
                } catch ( IOException e ) {
                    Log.w( sTag, e.getMessage() );
                }
            } else {
                Log.w( sTag, "Could not create cache directory: "
                        + destFile.getParentFile() );
            }
        } else {
            Log.w( sTag, String.format( "Skipping existing file in cache: %s to: %s",
                    assetPath, destFile ) );
        }
    }

    private JSONObject getConfigFromFile(String key) {
        JSONObject obj = null;
        try (
            InputStream is = getAssets().open(sDeviceConfigFile)
        ) {
            byte[] buffer = new byte[ is.available() ];
            is.read( buffer );
            String json = new String( buffer, "UTF-8" );
            obj = new JSONObject( json );
        } catch ( Exception e ) {
            Log.w( sTag, String.format( "Cannot read %s from assets directory. Error: %s",
                    sDeviceConfigFile, e.getMessage() ) );
        }

        JSONObject config = null;
        if ( obj != null ) {
            try {
                config = obj.getJSONObject( key );
            } catch ( JSONException e ) {
                Log.w( sTag, "No device config specified in " + sDeviceConfigFile );
            }
        }
        return config;
    }

    private void updateDevicePreferences( String clientId,
                                          String clientSecret,
                                          String productId,
                                          String productDsn ) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString( getString( R.string.preference_client_id ), clientId );
        editor.putString( getString( R.string.preference_client_secret ), clientSecret );
        editor.putString( getString( R.string.preference_product_id ), productId );
        editor.putString( getString( R.string.preference_product_dsn ), productDsn );
        editor.apply();
    }

    private void updateDeviceConfigGUI( final String clientId,
                                        final String clientSecret,
                                        final String productId,
                                        final String productDsn ) {
        runOnUiThread( new Runnable() {
            @Override
            public void run() {
                ( ( TextView ) findViewById( R.id.clientId ) ).setText( clientId );
                ( ( TextView ) findViewById( R.id.clientSecret ) ).setText( clientSecret );
                ( ( TextView ) findViewById( R.id.productId ) ).setText( productId );
                ( ( TextView ) findViewById( R.id.productDsn ) ).setText( productDsn );
            }
        });
    }

    /* Sample Metrics Code */
//    // Retrieve metrics config from config file and create configuration object
//    private MetricUploadConfiguration initializeMetricsConfiguration() {
//        String amazonId = "", productDsn = "";
//        boolean metricsEnabled = false;
//
//        JSONObject config = getConfigFromFile("metrics");
//        if ( config != null ) {
//            try {
//                amazonId = config.getString( "amazonId" );
//                metricsEnabled = config.getBoolean("metricsEnabled");
//            } catch ( JSONException e ) {
//                Log.w( sTag, "Missing metrics config info in app_config.json" );
//            }
//        }
//        productDsn = mPreferences.getString( this.getString( R.string.preference_product_dsn ), "" );
//        try {
//            return MetricUploadConfiguration.createMetricUploadConfiguration(amazonId, productDsn, metricsEnabled);
//        } catch (IllegalArgumentException| NullPointerException ex ) {
//            Log.e(sTag, "", ex);
//            throw ex;
//        }
//    }
}
