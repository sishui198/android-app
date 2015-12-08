package hu.ektf.iot.openbiomapsapp.adapter;

import android.content.Context;
import android.database.Cursor;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;

import hu.ektf.iot.openbiomapsapp.R;
import hu.ektf.iot.openbiomapsapp.database.NoteCreator;
import hu.ektf.iot.openbiomapsapp.object.Note;

/**
 * Created by Pádi on 2015. 12. 04..
 */
public class MyUploadListCursorAdapter extends CursorRecyclerViewAdapter<MyUploadListCursorAdapter.ViewHolder>{

    private AdapterView.OnItemClickListener itemClickListener;

    public MyUploadListCursorAdapter(Context context,Cursor cursor){
        super(context,cursor);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public TextView tvComment;
        public TextView tvDate;
        public ImageView ivSounds, ivImages, ivStatus;
        public TextView tvCoord;
        public TextView tvNumOfImages, tvNumOfSounds;

        public AdapterView.OnItemClickListener itemClickListener;

        public ViewHolder(View v, AdapterView.OnItemClickListener itemClickListener) {
            super(v);
            this.itemClickListener = itemClickListener;
            tvComment = (TextView) v.findViewById(R.id.tvNote);
            tvDate = (TextView) v.findViewById(R.id.tvDate);
            ivSounds = (ImageView) v.findViewById(R.id.ivSounds);
            ivImages = (ImageView) v.findViewById(R.id.ivImages);
            ivStatus = (ImageView) v.findViewById(R.id.ivStatus);
            tvCoord = (TextView) v.findViewById(R.id.tvLocation);
            tvNumOfImages = (TextView) v.findViewById(R.id.tvNumOfImages);
            tvNumOfSounds = (TextView) v.findViewById(R.id.tvNumOfSounds);
            v.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(null, v, getPosition(), v.getId());
            }
        }
    }

    public void setOnItemClickListener(AdapterView.OnItemClickListener itemClickListener) {
        this.itemClickListener = itemClickListener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_upload, parent, false);
        ViewHolder vh = new ViewHolder(itemView, itemClickListener);
        return vh;
    }



    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Cursor cursor) {
       Note myListItem = NoteCreator.getNoteFromCursor(cursor);
        if(myListItem.getDate() != null) viewHolder.tvDate.setText(myListItem.getDate().toString());
        if(!myListItem.getComment().isEmpty()) viewHolder.tvComment.setText(myListItem.getComment());
        if(!myListItem.getLocationString().isEmpty()) viewHolder.tvCoord.setText(myListItem.getLocationString());
        if(myListItem.getResponse() == 0) {
            viewHolder.ivStatus.setImageResource(R.drawable.fail);
        }
        if(myListItem.getImagesList() != null) viewHolder.tvNumOfImages.setText(String.valueOf(myListItem.getImagesList().size()));
        if(myListItem.getSoundsList() != null) viewHolder.tvNumOfSounds.setText(String.valueOf(myListItem.getSoundsList().size()));
    }
}
