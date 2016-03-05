package com.habitrpg.android.habitica.ui;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.github.data5tream.emojilib.EmojiEditText;
import com.habitrpg.android.habitica.R;
import com.habitrpg.android.habitica.events.commands.CreateTagCommand;
import com.habitrpg.android.habitica.ui.helpers.ViewHelper;
import com.mikepenz.materialdrawer.model.BasePrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.BaseViewHolder;
import com.mikepenz.fastadapter.utils.ViewHolderFactory;

import butterknife.Bind;
import butterknife.ButterKnife;
import org.greenrobot.eventbus.EventBus;

public class EditTextDrawer extends BasePrimaryDrawerItem<EditTextDrawer, EditTextDrawer.ViewHolder> {
    @Override
    public int getType() {
        return R.id.material_drawer_item_primary;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.edit_text_drawer_item;
    }

    @Override
    public void bindView(ViewHolder holder) {
        onPostBindView(this, holder.itemView);
    }

    @Override
    public ViewHolderFactory getFactory() {
        return new ItemFactory();
    }

    public static class ItemFactory implements ViewHolderFactory<EditTextDrawer.ViewHolder> {
        public ViewHolder create(View v) {
            return new ViewHolder(v);
        }
    }

    public static class ViewHolder extends BaseViewHolder implements View.OnClickListener {

        View view;

        @Bind(R.id.editText)
        EditText editText;

        @Bind(R.id.btnAdd)
        Button btnAdd;

        private ViewHolder(View view) {
            super(view);
            this.view = view;
            ButterKnife.bind(this, view);

            ViewHelper.SetBackgroundTint(btnAdd, view.getResources().getColor(R.color.brand));

            btnAdd.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            String text = editText.getText().toString();

            if (text.equals(""))
                return;

            EventBus.getDefault().post(new CreateTagCommand(editText.getText().toString()));

            editText.setText("");
        }
    }
}
