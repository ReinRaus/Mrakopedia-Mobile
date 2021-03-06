package com.randomname.mrakopedia.ui.recentchanges;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.randomname.mrakopedia.MainActivity;
import com.randomname.mrakopedia.MrakopediaApplication;
import com.randomname.mrakopedia.R;
import com.randomname.mrakopedia.api.MrakopediaApiWorker;
import com.randomname.mrakopedia.models.api.recentchanges.RecentChangesResult;
import com.randomname.mrakopedia.models.api.recentchanges.Recentchanges;
import com.randomname.mrakopedia.realm.DBWorker;
import com.randomname.mrakopedia.ui.RxBaseFragment;
import com.randomname.mrakopedia.ui.pagesummary.PageSummaryActivity;
import com.randomname.mrakopedia.ui.views.EndlessRecyclerOnScrollListener;
import com.randomname.mrakopedia.utils.NetworkUtils;
import com.randomname.mrakopedia.utils.Utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import butterknife.Bind;
import butterknife.ButterKnife;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Created by Vlad on 27.01.2016.
 */
public class RecentChangesFragment extends RxBaseFragment {

    private static final String TAG = "Recent changes Fragment";
    private static final int PAGE_SUMMARY_ACTIVITY_CODE = 11;
    private static final String RECENT_CHANGES_ARRAY_LIST_KEY = "recentChangesArrayListKey";
    private static final String CONTINUE_STRING_KEY = "continueStringKey";
    private static final String SELECTED_POSITION_KEY = "selectedPositionKey";

    private String continueString = "";
    private ArrayList<Recentchanges> recentChangesArrayList;
    private RecentChangesAdapter adapter;
    private int selectedPosition = 0;
    private boolean isLoading = false;

    @Bind(R.id.recent_changes_recycler_view)
    RecyclerView recyclerView;
    @Bind(R.id.error_text_view)
    carbon.widget.TextView errorTextView;

    private Tracker mTracker;

    public RecentChangesFragment () {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            recentChangesArrayList = savedInstanceState.getParcelableArrayList(RECENT_CHANGES_ARRAY_LIST_KEY);
            continueString = savedInstanceState.getString(CONTINUE_STRING_KEY);
            selectedPosition = savedInstanceState.getInt(SELECTED_POSITION_KEY, 0);

            if (recentChangesArrayList == null) {
                recentChangesArrayList = new ArrayList<>();
            }
        } else {
            recentChangesArrayList = new ArrayList<>();
        }

        MrakopediaApplication application = (MrakopediaApplication)getActivity().getApplication();
        mTracker = application.getDefaultTracker();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.recent_changes_fragment, null);
        ButterKnife.bind(this, view);

        adapter = new RecentChangesAdapter(recentChangesArrayList, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedPosition = recyclerView.getChildAdapterPosition(v) - 1;
                Intent intent = new Intent(getActivity(), PageSummaryActivity.class);
                intent.putExtra(PageSummaryActivity.PAGE_NAME_EXTRA, adapter.getDisplayedData().get(selectedPosition).getTitle());
                intent.putExtra(PageSummaryActivity.PAGE_ID_EXTRA, adapter.getDisplayedData().get(selectedPosition).getPageid());

                startActivityForResult(intent, PAGE_SUMMARY_ACTIVITY_CODE);
            }
        });
        LinearLayoutManager manager = new LinearLayoutManager(getActivity());
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(manager);
        recyclerView.addOnScrollListener(new EndlessRecyclerOnScrollListener(manager) {
            @Override
            public void onLoadMore(int current_page) {
                getRecentChanges();
            }
        });
        recyclerView.addOnScrollListener(((MainActivity) getActivity()).toolbarHideRecyclerOnScrollListener);

        if (recentChangesArrayList.isEmpty()) {
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    getRecentChanges();
                }
            }, 100);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        mTracker.setScreenName(TAG);
        mTracker.send(new HitBuilders.ScreenViewBuilder().build());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList(RECENT_CHANGES_ARRAY_LIST_KEY, recentChangesArrayList);
        outState.putString(CONTINUE_STRING_KEY, continueString);
        outState.putInt(SELECTED_POSITION_KEY, selectedPosition);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PAGE_SUMMARY_ACTIVITY_CODE) {
            adapter.getDisplayedData().get(selectedPosition).setIsViewed(DBWorker.getPageIsRead(adapter.getDisplayedData().get(selectedPosition).getTitle()));
            adapter.notifyDataSetChanged();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void getRecentChanges() {
        if ((!adapter.getDisplayedData().isEmpty() && continueString == null) || isLoading) {
            return;
        }
        Log.e(TAG, "start downloading");

        isLoading = true;

        String savedString = continueString;
        continueString = null;

        Subscription subscription =
                MrakopediaApiWorker
                        .getInstance()
                        .getRecentChanges(savedString)
                        .doOnNext(new Action1<RecentChangesResult>() {
                            @Override
                            public void call(RecentChangesResult recentChangesResult) {
                                if (recentChangesResult.getmContinue() != null) {
                                    continueString = recentChangesResult.getmContinue().getRccontinue();
                                } else {
                                    continueString = null;
                                }
                            }
                        })
                        .flatMap(new Func1<RecentChangesResult, Observable<Recentchanges>>() {
                            @Override
                            public Observable<Recentchanges> call(RecentChangesResult recentChangesResult) {
                                return Observable.from(recentChangesResult.getQuery().getRecentchanges());
                            }
                        })
                        .filter(new Func1<Recentchanges, Boolean>() {
                            @Override
                            public Boolean call(Recentchanges recentchanges) {
                                for (String banString : Utils.pagesBanList) {
                                    if (recentchanges.getTitle().toLowerCase().contains(banString.toLowerCase())) {
                                        return false;
                                    }
                                }
                                return true;
                            }
                        })
                        .filter(new Func1<Recentchanges, Boolean>() {
                            @Override
                            public Boolean call(Recentchanges recentchanges) {
                                return recentchanges.getRedirect() == null;
                            }
                        })
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Subscriber<Recentchanges>() {
                            @Override
                            public void onCompleted() {
                                if (recyclerView.getVisibility() != View.VISIBLE) {
                                    recyclerView.setVisibility(View.VISIBLE);
                                    errorTextView.setVisibility(View.GONE);
                                }
                                isLoading = false;
                            }

                            @Override
                            public void onError(Throwable e) {
                                e.printStackTrace();
                                isLoading = false;
                                if (adapter.getDisplayedData().size() <= 1) {
                                    errorTextView.setVisibility(View.VISIBLE);

                                    if (!NetworkUtils.isInternetAvailable(getActivity())) {
                                        errorTextView.setText(getString(R.string.error_loading_recent_articles) + " " + getString(R.string.no_internet_text));
                                    }
                                    recyclerView.setVisibility(View.GONE);
                                } else {
                                    Toast.makeText(getActivity(), getString(R.string.error_loading_recent_articles) + " " + getString(R.string.no_internet_text), Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onNext(Recentchanges recentchanges) {
                                adapter.getDisplayedData().add(recentchanges);
                                adapter.notifyItemInserted(adapter.getDisplayedData().indexOf(recentchanges) + 1);
                                checkIfPageWasRead(recentchanges);
                            }
                        });

        bindToLifecycle(subscription);
    }

    @Override
    public void onConnectedToInternet() {
        if (adapter.getDisplayedData().isEmpty()) {
            getRecentChanges();
        }
    }

    private void checkIfPageWasRead(final Recentchanges recentChange) {
        Subscription subscription =
                Observable.
                        just(recentChange)
                        .flatMap(new Func1<Recentchanges, Observable<Boolean>>() {
                            @Override
                            public Observable<Boolean> call(Recentchanges recentchanges) {
                                return Observable.just(DBWorker.getPageIsRead(recentchanges.getTitle()));
                            }
                        })
                        .filter(new Func1<Boolean, Boolean>() {
                            @Override
                            public Boolean call(Boolean aBoolean) {
                                return aBoolean;
                            }
                        })
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Subscriber<Boolean>() {
                            @Override
                            public void onCompleted() {

                            }

                            @Override
                            public void onError(Throwable e) {
                                e.printStackTrace();
                            }

                            @Override
                            public void onNext(Boolean aBoolean) {
                                if (aBoolean) {
                                    adapter.getDisplayedData()
                                            .get(adapter.getDisplayedData()
                                            .indexOf(recentChange))
                                            .setIsViewed(aBoolean);

                                    adapter.notifyItemChanged(adapter.getDisplayedData().indexOf(recentChange) + 1);
                                }
                            }
                        });

        bindToLifecycle(subscription);

    }

    private class RecentChangesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        private SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMMM");

        private ArrayList<Recentchanges> recentChangesArrayList;

        private View.OnClickListener onClickListener;

        private final static int LIST_ITEM_TYPE = 0;
        private final static int SPACER_ITEM_TYPE = 1;

        public RecentChangesAdapter(ArrayList<Recentchanges> recentChangesArrayList, View.OnClickListener onClickListener) {
            this.recentChangesArrayList = recentChangesArrayList;
            this.onClickListener = onClickListener;

            notifyItemInserted(0);
        }

        public ArrayList<Recentchanges> getDisplayedData() {
            return recentChangesArrayList;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return SPACER_ITEM_TYPE;
            } else {
                return LIST_ITEM_TYPE;
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;

            if (viewType == SPACER_ITEM_TYPE) {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.spacer_view_holder, parent, false);
                return new SpacerViewHolder(view);
            }

            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recent_changes_view_holder, parent, false);
            view.setOnClickListener(onClickListener);
            return new ListItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (position == 0) {
                return;
            }

            String stringDate = recentChangesArrayList.get(position - 1).getTimestamp();
            Date dateStr = null;

            try {
                dateStr = format.parse(stringDate);
            } catch (ParseException e) {
                e.printStackTrace();
            }

            ((ListItemViewHolder)holder).titleTextView.setText(recentChangesArrayList.get(position - 1).getTitle());

            if (dateStr != null) {
                ((ListItemViewHolder)holder).changeDateTextView.setText(getString(R.string.recent_changes_added) + " " + outputFormat.format(dateStr));
            }

            if (recentChangesArrayList.get(position - 1).isViewed()) {
                ((ListItemViewHolder)holder).titleTextView.setTextColor(getResources().getColor(R.color.colorPrimary));
            } else {
                ((ListItemViewHolder)holder).titleTextView.setTextColor(Color.parseColor("#D9000000"));
            }
        }

        @Override
        public int getItemCount() {
            return recentChangesArrayList == null ? 0 : recentChangesArrayList.size() + 1;
        }

        private class SpacerViewHolder extends RecyclerView.ViewHolder {
            public SpacerViewHolder(View itemView) {
                super(itemView);
            }
        }

        protected class ListItemViewHolder extends RecyclerView.ViewHolder {

            public TextView titleTextView;
            public TextView changeDateTextView;

            public ListItemViewHolder(View itemView) {
                super(itemView);
                titleTextView = (TextView) itemView.findViewById(R.id.title_text_view);
                changeDateTextView = (TextView) itemView.findViewById(R.id.change_date_text_view);
            }
        }
    }
}
