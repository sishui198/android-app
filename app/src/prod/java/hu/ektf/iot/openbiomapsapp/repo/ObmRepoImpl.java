package hu.ektf.iot.openbiomapsapp.repo;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import javax.inject.Inject;

import hu.ektf.iot.openbiomapsapp.BioMapsApplication;
import hu.ektf.iot.openbiomapsapp.model.Form;
import hu.ektf.iot.openbiomapsapp.model.FormControl;
import hu.ektf.iot.openbiomapsapp.model.FormData;
import hu.ektf.iot.openbiomapsapp.model.response.FormControlResponse;
import hu.ektf.iot.openbiomapsapp.model.response.FormResponse;
import hu.ektf.iot.openbiomapsapp.model.response.TokenResponse;
import hu.ektf.iot.openbiomapsapp.repo.api.DynamicEndpoint;
import hu.ektf.iot.openbiomapsapp.repo.api.ObmApi;
import hu.ektf.iot.openbiomapsapp.repo.database.AppDatabase;
import hu.ektf.iot.openbiomapsapp.repo.database.StorageHelper;
import retrofit.client.Response;
import rx.Completable;
import rx.Observable;
import timber.log.Timber;

public class ObmRepoImpl extends ObmRepo {
    private static final String CLIENT_ID = "mobile";
    private static final String CLIENT_SECRET = "123";
    private static final String GRANT_TYPE_PASSWORD = "password";
    private static final String GRANT_TYPE_REFRESH = "refresh_token";
    private static final String SCOPE = "get_form_list get_form_data put_data";

    private final BioMapsApplication application;
    private final ObmApi api;
    private final StorageHelper storage;
    private final AppDatabase database;
    private final DynamicEndpoint endpoint;

    private String projectName;

    @Inject
    public ObmRepoImpl(BioMapsApplication application, ObmApi api, StorageHelper storage, AppDatabase database, DynamicEndpoint endpoint) {
        this.application = application;
        this.api = api;
        this.storage = storage;
        this.database = database;
        this.endpoint = endpoint;

        this.projectName = storage.getProjectName();
        endpoint.setUrl(storage.getServerUrl());
    }

    @Override
    public Completable setUrl(String url) {
        return Completable.fromAction(() -> {
            Uri uri = Uri.parse(url);
            String baseUrl = uri.getScheme() + "://" + uri.getHost();
            String projectName = uri.getLastPathSegment();

            storage.setServerUrl(baseUrl);
            storage.setProjectName(projectName);

            endpoint.setUrl(baseUrl);
            this.projectName = projectName;
        });
    }

    @Override
    public boolean isLoggedIn() {
        return storage.hasAccessToken();
    }

    @Override
    public Observable<TokenResponse> login(String username, String password) {
        return api.login(username, password, CLIENT_ID, CLIENT_SECRET, GRANT_TYPE_PASSWORD, SCOPE)
                .doOnNext(tokenResponse -> {
                    storage.setAccessToken(tokenResponse.getAccessToken());
                    storage.setRefreshToken(tokenResponse.getRefreshToken());
                });
    }

    @Override
    public Completable logout() {
        return Completable.fromAction(() -> {
            storage.clearTokens();
        });
    }

    public TokenResponse refreshToken(String refreshToken) {
        TokenResponse tokenResponse = api.refreshToken(refreshToken, CLIENT_ID, CLIENT_SECRET, GRANT_TYPE_REFRESH, SCOPE);
        storage.setAccessToken(tokenResponse.getAccessToken());
        storage.setRefreshToken(tokenResponse.getRefreshToken());
        return tokenResponse;
    }

    @Override
    public Observable<List<Form>> loadFormList() {
        return api.getForms(projectName, "get_form_list")
                .map(FormResponse::getData)
                .doOnNext(forms -> {
                    for (Form form : forms) {
                        form.setProjectName(projectName);
                    }
                })
                .doOnNext(forms -> {
                    database.formDao().deleteAll();
                    database.formDao().insertAll(forms);
                })
                .onErrorReturn(throwable -> database.formDao()
                        .getFormsByProjectName(projectName));
    }

    @Override
    public Observable<List<FormControl>> loadForm(int formId) {
        return loadForm(formId, null);
    }

    @Override
    public Observable<List<FormControl>> loadForm(int formId, Integer formDataId) {
        return api.getForm(projectName, "get_form_data", formId)
                .map(FormControlResponse::getData)
                .doOnNext(formControls -> {
                    Form form = database.formDao()
                            .getForm(projectName, formId);
                    form.setFormControls(formControls);
                    database.formDao().update(form);
                })
                .onErrorReturn(throwable -> database.formDao()
                        .getForm(projectName, formId)
                        .getFormControls())
                .map(formControls -> {
                    if (formDataId == null) {
                        return formControls;
                    }

                    FormData formData = getSavedFormData(formDataId);
                    return populateFormControls(formControls, formData);
                });
    }

    private List<FormControl> populateFormControls(List<FormControl> formControls, FormData formData) {
        if (formData == null) {
            return formControls;
        }

        try {
            JSONArray jsonArray = new JSONArray(formData.getJson());
            JSONObject json = jsonArray.getJSONObject(0);

            for (FormControl formControl : formControls) {
                formControl.setValue(json.get(formControl.getColumn()));
            }
        } catch (JSONException e) {
            Timber.e(e, "Could not populate FormControls");
        }

        return formControls;
    }

    @Override
    public List<FormData> getSavedFormData() {
        return database.formDataDao().getFormDataList();
    }

    @Override
    public Observable<List<FormData>> getSavedFormDataAsync() {
        return Observable.fromCallable(() -> database.formDataDao().getFormDataList());
    }

    @Override
    public FormData getSavedFormData(int id) {
        return database.formDataDao().getFormData(id);
    }

    @Override
    public FormData getSavedFormDataByState(FormData.State state) {
        return database.formDataDao().getFormDataListByState(state);
    }

    @Override
    public Completable saveData(final FormData formData) {
                return Completable.fromAction(() -> {
                    formData.setProjectName(storage.getProjectName());
                    database.formDataDao().insert(formData);
                })
                .doOnCompleted(application::requestSync);
    }

    @Override
    public Completable deleteData(FormData formData) {
        return Completable.fromAction(() -> database.formDataDao().delete(formData));
    }

    @Override
    public Response uploadData(int formId, String columns, String values) {
        return api.putData(projectName, "put_data", formId, columns, values);
    }
}
