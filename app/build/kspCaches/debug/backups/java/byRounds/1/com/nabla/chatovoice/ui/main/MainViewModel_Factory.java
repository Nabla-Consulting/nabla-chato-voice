package com.nabla.chatovoice.ui.main;

import com.nabla.chatovoice.data.remote.ChatoGatewayRepository;
import com.nabla.chatovoice.domain.repository.GatewayRepository;
import com.nabla.chatovoice.service.TextToSpeechManager;
import com.nabla.chatovoice.service.VoiceInputManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
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
public final class MainViewModel_Factory implements Factory<MainViewModel> {
  private final Provider<GatewayRepository> gatewayRepositoryProvider;

  private final Provider<VoiceInputManager> voiceInputManagerProvider;

  private final Provider<TextToSpeechManager> ttsManagerProvider;

  private final Provider<ChatoGatewayRepository> chatoGatewayRepositoryProvider;

  public MainViewModel_Factory(Provider<GatewayRepository> gatewayRepositoryProvider,
      Provider<VoiceInputManager> voiceInputManagerProvider,
      Provider<TextToSpeechManager> ttsManagerProvider,
      Provider<ChatoGatewayRepository> chatoGatewayRepositoryProvider) {
    this.gatewayRepositoryProvider = gatewayRepositoryProvider;
    this.voiceInputManagerProvider = voiceInputManagerProvider;
    this.ttsManagerProvider = ttsManagerProvider;
    this.chatoGatewayRepositoryProvider = chatoGatewayRepositoryProvider;
  }

  @Override
  public MainViewModel get() {
    return newInstance(gatewayRepositoryProvider.get(), voiceInputManagerProvider.get(), ttsManagerProvider.get(), chatoGatewayRepositoryProvider.get());
  }

  public static MainViewModel_Factory create(Provider<GatewayRepository> gatewayRepositoryProvider,
      Provider<VoiceInputManager> voiceInputManagerProvider,
      Provider<TextToSpeechManager> ttsManagerProvider,
      Provider<ChatoGatewayRepository> chatoGatewayRepositoryProvider) {
    return new MainViewModel_Factory(gatewayRepositoryProvider, voiceInputManagerProvider, ttsManagerProvider, chatoGatewayRepositoryProvider);
  }

  public static MainViewModel newInstance(GatewayRepository gatewayRepository,
      VoiceInputManager voiceInputManager, TextToSpeechManager ttsManager,
      ChatoGatewayRepository chatoGatewayRepository) {
    return new MainViewModel(gatewayRepository, voiceInputManager, ttsManager, chatoGatewayRepository);
  }
}
