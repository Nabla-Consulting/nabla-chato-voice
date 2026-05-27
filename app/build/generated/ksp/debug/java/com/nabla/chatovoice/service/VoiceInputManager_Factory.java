package com.nabla.chatovoice.service;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class VoiceInputManager_Factory implements Factory<VoiceInputManager> {
  private final Provider<Context> contextProvider;

  public VoiceInputManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public VoiceInputManager get() {
    return newInstance(contextProvider.get());
  }

  public static VoiceInputManager_Factory create(Provider<Context> contextProvider) {
    return new VoiceInputManager_Factory(contextProvider);
  }

  public static VoiceInputManager newInstance(Context context) {
    return new VoiceInputManager(context);
  }
}
