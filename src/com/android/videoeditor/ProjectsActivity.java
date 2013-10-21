/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.videoeditor;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.android.videoeditor.service.ApiService;
import com.android.videoeditor.service.ApiServiceListener;
import com.android.videoeditor.service.VideoEditorProject;
import com.android.videoeditor.util.FileUtils;

import java.util.List;

/**
 * Activity that lets user pick a project or create a new one.
 */
public class ProjectsActivity extends NoSearchActivity {
    private static final String LOG_TAG = "ProjectsActivity";

    private static final int REQUEST_CODE_OPEN_PROJECT = 1;
    private static final int REQUEST_CODE_CREATE_PROJECT = 2;

    // The project path returned by the picker
    public static final String PARAM_OPEN_PROJECT_PATH = "path";
    public static final String PARAM_CREATE_PROJECT_NAME = "name";

    // Menu ids
    private static final int MENU_NEW_PROJECT_ID = 1;

    // Dialog ids
    private static final int DIALOG_NEW_PROJECT_ID = 1;
    private static final int DIALOG_REMOVE_PROJECT_ID = 2;

    // Dialog parameters
    private static final String PARAM_DIALOG_PATH_ID = "path";

    // Threshold in width dip for showing title in action bar
    private static final int SHOW_TITLE_THRESHOLD_WIDTH_DIP = 1000;

    private GridView mGridView;
    private List<VideoEditorProject> mProjects;
    private ProjectPickerAdapter mAdapter;

    // Listener that responds to the event when projects are loaded. It populates the grid view with
    // project thumbnail and information.
    private final ApiServiceListener mProjectsLoadedListener = new ApiServiceListener() {
        @Override
        public void onProjectsLoaded(List<VideoEditorProject> projects, Exception exception) {
            if (projects != null && exception == null) {
                mProjects = projects;
                // Initialize adapter with project list and populate data in the grid view.
                mAdapter = new ProjectPickerAdapter(ProjectsActivity.this, getLayoutInflater(), projects);
                mGridView.setAdapter(mAdapter);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.project_picker);

        // Show action bar title only on large screens.
        final ActionBar actionBar = getActionBar();
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        final int widthDip = (int) (displayMetrics.widthPixels / displayMetrics.scaledDensity);
        // Only show title on large screens (width >= 1000 dip).
        if (widthDip >= SHOW_TITLE_THRESHOLD_WIDTH_DIP) {
            actionBar.setDisplayOptions(actionBar.getDisplayOptions() |
                    ActionBar.DISPLAY_SHOW_TITLE);
            actionBar.setTitle(R.string.full_app_name);
        }

        mGridView = (GridView) findViewById(R.id.projects);
        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                // If user clicks on the last item, then create a new project.
                if (position == mProjects.size()) {
                    showDialog(DIALOG_NEW_PROJECT_ID);
                } else {
                    openProject(mProjects.get(position).getPath());
                }
            }
        });
        // Upon long press, pop up a menu with a removal option.
        mGridView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                                           final int position, long id) {
                // Open new project dialog when user clicks on the "create new project" card.
                if (position == mProjects.size()) {
                    showDialog(DIALOG_NEW_PROJECT_ID);
                    return true;
                }
                // Otherwise, pop up a menu with a project removal option.
                final PopupMenu popupMenu = new PopupMenu(ProjectsActivity.this, view);
                popupMenu.getMenuInflater().inflate(R.menu.project_menu, popupMenu.getMenu());
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.action_remove_project:
                                final Bundle bundle = new Bundle();
                                bundle.putString(PARAM_DIALOG_PATH_ID,
                                        mProjects.get(position).getPath());
                                showDialog(DIALOG_REMOVE_PROJECT_ID, bundle);
                                break;
                            default:
                                break;
                        }
                        return true;
                    }
                });
                popupMenu.show();
                return true;
            }});
    }

    @Override
    public void onResume() {
        super.onResume();
        ApiService.registerListener(mProjectsLoadedListener);
        ApiService.loadProjects(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        ApiService.unregisterListener(mProjectsLoadedListener);
        mAdapter = null;
        mGridView.setAdapter(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_NEW_PROJECT_ID, Menu.NONE, R.string.projects_new_project)
                .setIcon(R.drawable.ic_menu_add_video)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_NEW_PROJECT_ID: {
                showDialog(DIALOG_NEW_PROJECT_ID);
                return true;
            }

            default: {
                return false;
            }
        }
    }

    @Override
    public Dialog onCreateDialog(int id, final Bundle bundle) {
        switch (id) {
            case DIALOG_NEW_PROJECT_ID: {
                return AlertDialogs.createEditDialog(
                        this,
                        null,  // No title
                        null,  // No text in the edit box
                        getString(android.R.string.ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                final TextView tv =
                                    (TextView)((AlertDialog)dialog).findViewById(R.id.text_1);
                                final String projectName = tv.getText().toString();
                                removeDialog(DIALOG_NEW_PROJECT_ID);
                                createProject(projectName);
                            }
                        },
                        getString(android.R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                removeDialog(DIALOG_NEW_PROJECT_ID);
                            }
                        },
                        new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                removeDialog(DIALOG_NEW_PROJECT_ID);
                            }
                        },
                        InputType.TYPE_NULL,
                        32,
                        getString(R.string.projects_project_name));
            }

            case DIALOG_REMOVE_PROJECT_ID: {
                final String projectPath = bundle.getString(PARAM_DIALOG_PATH_ID);
                return AlertDialogs.createAlert(
                        this,
                        getString(R.string.editor_delete_project),
                        0,  // no icons for this dialog.
                        getString(R.string.editor_delete_project_question),
                        getString(R.string.yes),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                removeDialog(DIALOG_REMOVE_PROJECT_ID);
                                deleteProject(projectPath);
                            }
                        },
                        getString(R.string.no),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                removeDialog(DIALOG_REMOVE_PROJECT_ID);
                            }
                        },
                        new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                removeDialog(DIALOG_REMOVE_PROJECT_ID);
                            }
                        },
                        true);
            }

            default: {
                return null;
            }
        }
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        switch (id) {
            // A workaround to handle the OK button. We can't access the button with getButton()
            // when building the dialog in AlertDialogs, hence we postpone the enabling/disabling
            // of the positive button until the dialog is to be shown here.
            case DIALOG_NEW_PROJECT_ID:
                final AlertDialog newProjectDialog = (AlertDialog) dialog;
                Button positiveButton = newProjectDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                final TextView inputField =
                        (TextView) newProjectDialog.findViewById(R.id.text_1);
                positiveButton.setEnabled(inputField.getText().toString().trim().length() > 0);
            default:
                return;
        }
    }

    private void deleteProject(final String projectPath) {
        ApiService.deleteProject(ProjectsActivity.this, projectPath);
        mAdapter.remove(projectPath);
    }

    private void createProject(String projectName) {
        try {
            final Intent intent = new Intent(this, VideoEditorActivity.class);
            intent.setAction(Intent.ACTION_INSERT);
            intent.putExtra(PARAM_CREATE_PROJECT_NAME, projectName);
            final String projectPath = FileUtils.createNewProjectPath(this);
            intent.putExtra(PARAM_OPEN_PROJECT_PATH, projectPath);
            startActivityForResult(intent, REQUEST_CODE_CREATE_PROJECT);
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(this, R.string.editor_storage_not_available,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openProject(String projectPath) {
        final Intent intent = new Intent(this, VideoEditorActivity.class);
        intent.setAction(Intent.ACTION_EDIT);
        intent.putExtra(PARAM_OPEN_PROJECT_PATH, projectPath);
        startActivityForResult(intent, REQUEST_CODE_OPEN_PROJECT);
    }

    private static void logd(String message) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, message);
        }
    }
}
