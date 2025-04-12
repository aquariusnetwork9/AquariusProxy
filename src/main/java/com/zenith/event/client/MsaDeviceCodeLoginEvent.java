package com.zenith.event.client;

import net.raphimc.minecraftauth.step.msa.StepMsaDeviceCode;

public record MsaDeviceCodeLoginEvent(StepMsaDeviceCode.MsaDeviceCode deviceCode) { }
