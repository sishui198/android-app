package hu.ektf.iot.openbiomapsapp;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;

import hu.ektf.iot.openbiomapsapp.adapter.DividerItemDecoration;
import hu.ektf.iot.openbiomapsapp.adapter.NoteCursorAdapter;
import hu.ektf.iot.openbiomapsapp.database.BioMapsContentProvider;
import hu.ektf.iot.openbiomapsapp.database.BioMapsResolver;
import hu.ektf.iot.openbiomapsapp.database.NoteCreator;
import hu.ektf.iot.openbiomapsapp.database.NoteTable;
import hu.ektf.iot.openbiomapsapp.helper.ExportHelper;
import hu.ektf.iot.openbiomapsapp.helper.StorageHelper;
import hu.ektf.iot.openbiomapsapp.object.Note;

public class UploadActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final int NOTE_LOADER = 0;
    private static final int PROGRESS = 0x1;
    ProgressDialog barProgressDialog;
    private AsyncExport ae;
    private RecyclerView recyclerView;
    private NoteCursorAdapter adapter;
    private Button buttonExportAll;
    private TextView tvEmpty;
    private Button buttonUpload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        final StorageHelper sh = new StorageHelper(UploadActivity.this);

        // TODO Add emptyView for when the list is empty
        recyclerView = (RecyclerView) findViewById(R.id.recyclerUploadList);
        recyclerView.setHasFixedSize(true);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.addItemDecoration(
                new DividerItemDecoration(this, R.drawable.divider));
        buttonExportAll = (Button) findViewById(R.id.buttonExport);

        tvEmpty = (TextView) findViewById(R.id.textViewEmpty);
        buttonUpload = (Button) findViewById(R.id.buttonUpload);

        adapter = new NoteCursorAdapter(this, null);
        buttonExportAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AsyncExport ae = new AsyncExport();
                ae.execute();
            }
        });

        adapter.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int position, long id) {
                final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(UploadActivity.this);

                alertDialogBuilder.setTitle(getString(R.string.dialog_export_title));
                alertDialogBuilder.setMessage(getString(R.string.dialog_export_path) + "\n" + sh.getExportPath());

                alertDialogBuilder.setPositiveButton(getString(R.string.dialog_export_text_yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        Cursor c = adapter.getCursor();
                        c.moveToPosition(position);
                        Note n = NoteCreator.getNoteFromCursor(c);
                        try {
                            ExportHelper.exportNote(n);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

                alertDialogBuilder.setNegativeButton(getString(R.string.settings_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();
            }
        });

        recyclerView.setAdapter(adapter);

        getSupportLoaderManager().initLoader(NOTE_LOADER, null, this);

    }


    @Override
    public Loader<Cursor> onCreateLoader(int loaderID, Bundle args) {
        String selection = NoteTable.STATE + " != ?";
        String[] selectionArgs = new String[]{String.valueOf(Note.State.CREATED.getValue())};
        String order = NoteTable.DATE + " DESC";
        return new CursorLoader(
                this,                                      // Activity context
                BioMapsContentProvider.CONTENT_URI,        // Table to query
                null,                                      // Projection to return
                selection,                                 // No selection clause
                selectionArgs,                             // No selection arguments
                order                                      // Sort order
        );
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        adapter.changeCursor(data);
        if (adapter.getItemCount() == 0) {
            tvEmpty.setVisibility(View.VISIBLE);
            buttonExportAll.setVisibility(View.GONE);
            buttonUpload.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        adapter.changeCursor(null);
    }

    class AsyncExport extends AsyncTask<Void, Integer, String> {
        @Override
        protected String doInBackground(Void... params) {
            try {
                ArrayList<Note> allNote = new BioMapsResolver(UploadActivity.this).getAllNote();
                int count = allNote.size();
                for (int i = 0; i < count; i++) {
                    if (isCancelled()) break;
                    if (allNote.get(i).getState() == Note.State.CREATED) break;

                    ExportHelper.exportNote(allNote.get(i));
                    publishProgress((int) ((i / (float) count) * 100));
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "Task Completed.";
        }

        @Override
        protected void onPostExecute(String result) {
            barProgressDialog.hide();

        }

        @Override
        protected void onPreExecute() {
            barProgressDialog = new ProgressDialog(UploadActivity.this);
            barProgressDialog.setTitle(getString(R.string.export_progressbar_title));
            barProgressDialog.setMessage(getString(R.string.export_progressbar_progress));
            barProgressDialog.setProgressStyle(barProgressDialog.STYLE_HORIZONTAL);
            barProgressDialog.setProgress(0);
            barProgressDialog.setMax(100);
            barProgressDialog.setCancelable(false);
            barProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.export_progressbar_cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    if (ae != null) {
                        ae.cancel(false);
                        ae = null;
                    }
                }
            });
            barProgressDialog.show();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            barProgressDialog.setProgress(values[0]);
        }
    }
}
