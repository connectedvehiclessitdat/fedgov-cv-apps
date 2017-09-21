@echo off
SETLOCAL
REM java -cp "fedgov-cv-apps-1.0.0-SNAPSHOT-jar-with-dependencies.jar;." gov.usdot.cv.apps.sender.Sender -c isd_sender_config_ber_file.json
java -cp "fedgov-cv-apps-1.0.0-SNAPSHOT-jar-with-dependencies.jar;." gov.usdot.cv.apps.sender.Sender -c isd_sender_config_base64_file.json
ENDLOCAL
@echo on
