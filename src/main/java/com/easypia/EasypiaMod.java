package com.easypia;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EasypiaMod implements ModInitializer {
	public static final String MOD_ID = "stellive";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.initialize();
		DashHandler.initialize();
		LOGGER.info("Stellive 모드 로드 완료!");
	}
}
