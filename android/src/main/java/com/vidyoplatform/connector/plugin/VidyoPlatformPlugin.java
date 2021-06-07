package com.vidyoplatform.connector.plugin;

import android.Manifest;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.vidyo.VidyoClient.Connector.ConnectorPkg;
import com.vidyo.VidyoClient.Endpoint.Participant;
import com.vidyoplatform.connector.plugin.event.IPluginEventHandler;
import com.vidyoplatform.connector.plugin.utils.Logger;

@CapacitorPlugin(name = "VidyoPlatform",
        permissions = {
                @Permission(
                        alias = "camera",
                        strings = {Manifest.permission.CAMERA}
                ),
                @Permission(
                        alias = "audio",
                        strings = {Manifest.permission.RECORD_AUDIO}
                )
        })
public class VidyoPlatformPlugin extends Plugin implements IPluginEventHandler {

    private VideoFragment fragment;

    private final int containerViewId = 0x14;
    private boolean initialized = false;

    @PluginMethod()
    public void openConference(PluginCall call) {
        Logger.i("Plugin: OpenConf call: " + call.getData());

        if (getPermissionState("camera") != PermissionState.GRANTED && getPermissionState("audio") != PermissionState.GRANTED) {
            requestAllPermissions(call, "requiredPermissionsCallback");
            return;
        }

        initializeVidyo(call);
    }

    @PluginMethod()
    public void connect(PluginCall call) {
        try {
            fragment.connectOrDisconnect(true);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to connect");
        }
    }

    @PluginMethod()
    public void setPrivacy(PluginCall call) {
        try {
            String device = call.getString("device");
            Boolean privacy = call.getBoolean("privacy", false);

            switch (device) {
                case "camera":
                    fragment.setCameraPrivacy(privacy);
                    break;
                case "microphone":
                    fragment.setMicrophonePrivacy(privacy);
                    break;
            }

            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to set privacy");
        }
    }

    @PluginMethod()
    public void cycleCamera(PluginCall call) {
        try {
            fragment.cycleCamera();
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to cycle camera");
        }
    }

    @PluginMethod()
    public void disconnect(PluginCall call) {
        try {
            fragment.connectOrDisconnect(false);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to disconnect");
        }
    }

    @PluginMethod()
    public void closeConference(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            FrameLayout containerView = getBridge().getActivity().findViewById(containerViewId);
            if (containerView != null) {
                ((ViewGroup) getBridge().getWebView().getParent()).removeView(containerView);

//                getBridge().getWebView().setBackgroundColor(Color.WHITE);

                if (fragment != null) {
                    fragment.registerPluginEventHandler(null);

                    FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                    fragmentTransaction.remove(fragment);
                    fragmentTransaction.commit();
                    fragment = null;
                }

                call.resolve();
            } else {
                call.reject("Failed to close the conference");
            }
        });
    }

    private void initializeVidyo(PluginCall call) {
        call.resolve();

        if (!this.initialized)
            this.initialized = ConnectorPkg.initialize();

        String portal = call.getString("portal");
        String roomKey = call.getString("roomKey");
        String pin = call.getString("pin");
        String name = call.getString("name");

        Integer maxParticipants = call.getInt("maxParticipants");
        String logLevel = call.getString("logLevel");

        fragment = VideoFragment.open(portal, roomKey, pin, name, maxParticipants, logLevel);
        fragment.registerPluginEventHandler(this);

        bridge.getActivity().runOnUiThread(() -> {
            FrameLayout containerView = getBridge().getActivity().findViewById(containerViewId);

            if (containerView == null) {
                containerView = new FrameLayout(getActivity().getApplicationContext());
                containerView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                containerView.setId(containerViewId);

                getBridge().getWebView().setBackgroundColor(Color.TRANSPARENT);
                ((ViewGroup) getBridge().getWebView().getParent()).addView(containerView);

                getBridge().getWebView().getParent().bringChildToFront(getBridge().getWebView());

                FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.add(containerView.getId(), fragment);
                fragmentTransaction.commit();

                call.resolve();
            } else {
                call.reject("Conference already opened.");
            }
        });
    }

    @PermissionCallback()
    private void requiredPermissionsCallback(PluginCall call) {
        Logger.i("Plugin: Permissions callback: " + call.getCallbackId() + "/ Data: " + call.getData());
        initializeVidyo(call);
    }

    @Override
    public void onInitialized() {
        JSObject notifyObj = new JSObject();
        notifyObj.put("type", "init");
        notifyObj.put("status", true);
        notifyListeners("VidyoEventCallback", notifyObj);
    }

    @Override
    public void onConnected() {
        JSObject notifyObj = new JSObject();
        notifyObj.put("type", "connected");
        notifyListeners("VidyoEventCallback", notifyObj);
    }

    @Override
    public void onDisconnected(String reason) {
        JSObject notifyObj = new JSObject();
        notifyObj.put("type", "disconnected");
        notifyObj.put("reason", reason);
        notifyListeners("VidyoEventCallback", notifyObj);
    }

    @Override
    public void onFailure(String reason) {
        JSObject notifyObj = new JSObject();
        notifyObj.put("type", "failed");
        notifyObj.put("reason", reason);
        notifyListeners("VidyoEventCallback", notifyObj);
    }

    @Override
    public void onParticipantJoined(Participant participant) {
        JSObject notifyObj = new JSObject();
        notifyObj.put("type", "participant");
        notifyObj.put("action", "joined");
        notifyObj.put("name", participant.getName());
        notifyListeners("VidyoEventCallback", notifyObj);
    }

    @Override
    public void onParticipantLeft(Participant participant) {
        JSObject notifyObj = new JSObject();
        notifyObj.put("type", "participant");
        notifyObj.put("action", "left");
        notifyObj.put("name", participant.getName());
        notifyListeners("VidyoEventCallback", notifyObj);
    }
}