package com.villevalta.exampleapp.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;
import android.view.View;

import com.villevalta.exampleapp.ExampleApplication;
import com.villevalta.exampleapp.R;
import com.villevalta.exampleapp.adapter.ImagesAdapter;
import com.villevalta.exampleapp.model.Image;
import com.villevalta.exampleapp.model.Images;
import com.villevalta.exampleapp.model.Page;
import com.villevalta.exampleapp.network.service.ImgurApiService;
import com.villevalta.exampleapp.view.PaginatingRecyclerView;

import java.util.Date;

import io.realm.Realm;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements PaginatingRecyclerView.LoadMoreListener {

    public static final String TAG = "MainActivity";
    ImgurApiService apiService;

    private String subreddit = "funny";
    private String sort = "hot";

    private Images images;

    private Realm realm;

    private boolean loading = false;
    private Call<Page> pageCall;

    private PaginatingRecyclerView recycler;
    private ImagesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        apiService = ExampleApplication.getInstance().getImgurApiService();

        findViewById(R.id.clear_db).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                realm.beginTransaction();
                images.reset();
                realm.commitTransaction();
            }
        });

        recycler = (PaginatingRecyclerView) findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setLoadMoreListener(this);
        recycler.setPageLoadTriggerLimit(6);
        adapter = new ImagesAdapter();
        recycler.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume");

        realm = Realm.getDefaultInstance();

        // haetaan tallennetut kuvat realmista jos löytyy
        images = realm.where(Images.class).contains("id", subreddit + sort).findFirst();

        // Jos ei löytynyt, lisätään se sinne
        if (images == null) {
            images = new Images();
            images.setId(subreddit + sort);

            // Kaikki realm kirjoitukset pitää tapahtua transaction sisällä
            // Näillä komennoilla voidaan tehdä transactio synkronisesti
            realm.beginTransaction();
            images = realm.copyToRealm(images); // Realm palauttaa oman kopionsa, joten luetaan se takaisin muuttujaan
            realm.commitTransaction();
        }

        adapter.initialize(images);

        // Ensimmäinen sivuhaku, jos sivuja ei ole vielä haettu
        if (images.getPagesLoaded() == 0) {
            loadPage();
        } else {
            Log.d(TAG, "Images loaded from database: ");
            for (Image image : images.getImages()) {
                Log.d(TAG, "image: " + image.getTitle());
            }
        }

    }

    @Override
    protected void onPause() {

        if(images != null){
            images.removeAllChangeListeners();
        }

        cancelRequest();

        if (realm != null && !realm.isClosed()) {
            realm.close(); // Suljetaan realm
        }
        super.onPause();
    }

    private void cancelRequest() {
        if (pageCall != null && !pageCall.isCanceled()) {
            pageCall.cancel();
        }
    }

    private void setIsLoading(boolean loading){
        this.loading = loading;
        if(adapter != null){
            adapter.setShowLoading(loading);
        }
    }

    private void loadPage() {
        int page = images.getPagesLoaded();
        setIsLoading(true);
        pageCall = apiService.getImagesPage(subreddit, sort, page);
        pageCall.enqueue(new Callback<Page>() {
            @Override
            public void onResponse(Call<Page> call, Response<Page> response) {
                if (response != null && response.body().isSuccess()) {
                    realm.beginTransaction();
                    images.incrementPagesLoaded();
                    images.addImages(response.body().getData());
                    images.setLastUpdated(new Date().getTime());
                    realm.commitTransaction();
                    Log.d(TAG, "Images loaded from web: ");
                    for (Image image : images.getImages()) {
                        Log.d(TAG, "image: " + image.getTitle());
                    }
                }else{
                    Log.e(TAG, "onResponse: NO SUCCESS :(" );
                }
                setIsLoading(false);
            }

            @Override
            public void onFailure(Call<Page> call, Throwable t) {
                setIsLoading(false);
                Log.e(TAG, "onFailure:", t);
            }
        });

    }

    @Override
    public void shouldLoadMore() {
        if(!loading){
            loadPage();
        }
    }
}
