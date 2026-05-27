package com.nabla.chatovoice.data.remote;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava"
})
public final class ChatoGatewayRepository_Factory implements Factory<ChatoGatewayRepository> {
  private final Provider<Context> contextProvider;

  private final Provider<OkHttpClient> httpClientProvider;

  public ChatoGatewayRepository_Factory(Provider<Context> contextProvider,
      Provider<OkHttpClient> httpClientProvider) {
    this.contextProvider = contextProvider;
    this.httpClientProvider = httpClientProvider;
  }

  @Override
  public ChatoGatewayRepository get() {
    return newInstance(contextProvider.get(), httpClientProvider.get());
  }

  public static ChatoGatewayRepository_Factory create(Provider<Context> contextProvider,
      Provider<OkHttpClient> httpClientProvider) {
    return new ChatoGatewayRepository_Factory(contextProvider, httpClientProvider);
  }

  public static ChatoGatewayRepository newInstance(Context context, OkHttpClient httpClient) {
    return new ChatoGatewayRepository(context, httpClient);
  }
}
