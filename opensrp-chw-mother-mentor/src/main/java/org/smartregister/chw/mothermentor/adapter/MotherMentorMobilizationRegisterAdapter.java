package org.smartregister.chw.mothermentor.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.model.MotherMentorMobilizationModel;

import java.util.List;

public class MotherMentorMobilizationRegisterAdapter extends RecyclerView.Adapter<MotherMentorMobilizationRegisterAdapter.MotherMentorMobilzationViewHolder> {

    private final Context context;
    private final List<MotherMentorMobilizationModel> tbMotherMentorMobilizationModels;


    public MotherMentorMobilizationRegisterAdapter(List<MotherMentorMobilizationModel> tbMotherMentorMobilizationModels, Context context) {
        this.tbMotherMentorMobilizationModels = tbMotherMentorMobilizationModels;
        this.context = context;
    }

    @NonNull
    @Override
    public MotherMentorMobilzationViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        View followupLayout = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.mothermentor_mobilization_session_card_view, viewGroup, false);
        return new MotherMentorMobilzationViewHolder(followupLayout);
    }

    @Override
    public void onBindViewHolder(@NonNull MotherMentorMobilzationViewHolder holder, int position) {
        MotherMentorMobilizationModel tbMotherMentorMobilizationModel = tbMotherMentorMobilizationModels.get(position);
        holder.bindData(tbMotherMentorMobilizationModel);
    }

    @Override
    public int getItemCount() {
        return tbMotherMentorMobilizationModels.size();
    }

    protected class MotherMentorMobilzationViewHolder extends RecyclerView.ViewHolder {
        public TextView mobilizationSessionDate;
        public TextView mobilizationSessionParticipants;
//        public TextView mobilizationSessionCondomsIssued;

        public MotherMentorMobilzationViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        public void bindData(MotherMentorMobilizationModel tbMotherMentorMobilizationModel) {
            mobilizationSessionDate = itemView.findViewById(R.id.mobilization_session_date);
            mobilizationSessionParticipants = itemView.findViewById(R.id.mobilization_session_participants);
//            mobilizationSessionCondomsIssued = itemView.findViewById(R.id.mobilization_session_condoms_issued);

            mobilizationSessionDate.setText(context.getString(R.string.mobilziation_session_date, tbMotherMentorMobilizationModel.getSessionDate()));
            mobilizationSessionParticipants.setText(context.getString(R.string.mobilization_session_participants, tbMotherMentorMobilizationModel.getSessionParticipants()));
//            mobilizationSessionCondomsIssued.setText(context.getString(R.string.mobilization_condoms_issued, tbMotherMentorMobilizationModel.getCondomsIssued()));
        }
    }
}
