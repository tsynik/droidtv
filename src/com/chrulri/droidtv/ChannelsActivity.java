/******************************************************************************
 *  DroidTV, live TV on Android devices with host USB port and a DVB tuner    *
 *  Copyright (C) 2012  Christian Ulrich <chrulri@gmail.com>                  *
 *                                                                            *
 *  This program is free software: you can redistribute it and/or modify      *
 *  it under the terms of the GNU General Public License as published by      *
 *  the Free Software Foundation, either version 3 of the License, or         *
 *  (at your option) any later version.                                       *
 *                                                                            *
 *  This program is distributed in the hope that it will be useful,           *
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of            *
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             *
 *  GNU General Public License for more details.                              *
 *                                                                            *
 *  You should have received a copy of the GNU General Public License         *
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.     *
 ******************************************************************************/

package com.chrulri.droidtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.chrulri.droidtv.Utils.Prefs;
import com.chrulri.droidtv.Utils.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChannelsActivity extends Activity {
    private static final String TAG = ChannelsActivity.class.getSimpleName();
    private static final String PKG = ChannelsActivity.class.getPackage()
            .getName();

    public static final String ACTION_ERROR = PKG + ".ACTION_ERROR";
    public static final String ACTION_UPDATE = PKG + ".ACTION_UPDATE";

    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_UPDATES = "updates";
    public static final String EXTRA_ERRORMSG = "errormsg";

    private Spinner spinner;
    private ListView listView;
    private String[] channelConfigs;
    private String[] channels;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new CheckTask().execute();
    }

    private void gotoChannelsScreen() {
        setContentView(R.layout.channels);

        spinner = (Spinner) findViewById(R.id.spinner);
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                loadChannels((String) parent.getSelectedItem());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                loadChannels(null);
            }
        });

        listView = (ListView) findViewById(R.id.listView);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                    long id) {
                watchChannel((int) id);
            }
        });

        // start
        loadChannelLists();
        setChannelList(null);
        if (spinner.getAdapter().getCount() == 0) {
            Utils.openSettings(this);
        }
    }

    private void loadChannelLists() {
        // reset channel lists spinner
        spinner.setAdapter(null);
        listView.setAdapter(null);

        // load channel lists
        String[] configFiles = Utils.getConfigsDir(this).list();
        spinner.setAdapter(Utils.createSimpleArrayAdapter(this, configFiles));
    }

    private void loadChannels(String channelListName) {
        // reset channel list view
        listView.setAdapter(null);

        // check channel list
        File file = Utils.getConfigsFile(this, channelListName);
        if (file == null || !file.canRead() || !file.isFile()) {
            return;
        }

        // load channels
        channelConfigs = null;
        channels = null;
        try {
            List<String> channelConfigList = new ArrayList<String>();
            List<String> channelList = new ArrayList<String>();
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                channelConfigList.add(line);
                channelList.add(line.split(":")[0]);
            }
            reader.close();
            channelConfigs = channelConfigList.toArray(new String[channelConfigList
                    .size()]);
            channels = channelList.toArray(new String[channelList.size()]);
        } catch (IOException e) {
            Utils.error(this, getText(R.string.error_invalid_channel_configuration), e);
        }

        // update channel list view
        listView.setAdapter(new ArrayAdapter<String>(this, R.layout.list_item,
                channels));
    }

    /**
     * @param channelList list name, insert null to read config from settings
     */
    private void setChannelList(String channelList) {
        if (channelList == null) {
            channelList = Prefs.get(this).getString(Prefs.KEY_CHANNELCONFIGS, null);
        }

        if (StringUtils.isNullOrEmpty(channelList) || Utils.getConfigsFile(this,
                channelList) == null) {
            String[] files = Utils.getConfigsDir(this).list();
            if (files.length == 1) {
                channelList = files[0];
            } else {
                channelList = null;
            }
        }

        if (channelList == null) {
            spinner.setSelection(AdapterView.INVALID_POSITION);
        } else {
            for (int i = 0; i < spinner.getAdapter().getCount(); i++) {
                if (channelList.equals(spinner.getAdapter().getItem(i))) {
                    spinner.setSelection(i);
                    break;
                }
            }
        }
    }

    private void watchChannel(int channelId) {
        Log.d(TAG, "watchChannel(" + channelId + "): " + channels[channelId]);
        String channelConfig = channelConfigs[channelId];
        Intent intent = new Intent(this, StreamService.class);
        intent.putExtra(StreamService.EXTRA_CHANNELCONFIG, channelConfig);
        startService(intent);
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle(R.string.app_name)
                .setMessage("streaming")
                .setNegativeButton("Cancel", new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        stopService(new Intent(ChannelsActivity.this, StreamService.class));
                    }
                }).create();
        dlg.show();
    }

    private class CheckTask extends AsyncTask<Void, Integer, Integer> {

        static final int OK = 0;
        static final int CHECK_DEVICE = R.string.no_device;

        static final String FILE_DEV_DVB_ADAPTER = "/dev/dvb/adapter0";
        static final String FILE_DVB_CA = FILE_DEV_DVB_ADAPTER + "/ca0";
        static final String FILE_DVB_FRONTEND = FILE_DEV_DVB_ADAPTER + "/frontend0";
        static final String FILE_DVB_DEMUX = FILE_DEV_DVB_ADAPTER + "/demux0";
        static final String FILE_DVB_DVR = FILE_DEV_DVB_ADAPTER + "/dvr0";

        private boolean checkDeviceNode(String file, boolean checkExists) {
            File f = new File(file);
            if (checkExists) {
                return f.exists() && f.canRead() && f.canWrite();
            } else {
                return !f.exists() || f.exists() && f.canRead() && f.canWrite();
            }
        }

        private boolean checkDevice() {
            return checkDeviceNode(FILE_DVB_FRONTEND, true)
                    && checkDeviceNode(FILE_DVB_DEMUX, true)
                    && checkDeviceNode(FILE_DVB_DVR, true)
                    && checkDeviceNode(FILE_DVB_CA, false);
        }

        @Override
        protected void onPreExecute() {
            setContentView(R.layout.loading);
        }

        @Override
        protected Integer doInBackground(Void... params) {
            // check for device access
            publishProgress(CHECK_DEVICE);
            if (!checkDevice())
                return CHECK_DEVICE;
            // everything fine
            return OK;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            int value = values[0];
            TextView loading = (TextView) findViewById(R.id.loading);
            // update text
            switch (value) {
                case CHECK_DEVICE:
                    loading.setText(R.string.check_device);
                    break;
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == OK) {
                gotoChannelsScreen();
            } else {
                // show alert and abort application
                Utils.error(ChannelsActivity.this, getText(result), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                });
            }
        }
    }
}
