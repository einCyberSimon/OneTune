PACKAGE := de.eincybersimon.onetune
LINK_HOST ?= anytune.eincybersimon.de
SHARE_URL ?= https://open.spotify.com/track/5hjalT4nzp5RVWoJl9xqam
OPEN_PATH ?= apple/track/1561847490

.PHONY: help server reverse install share open logcat test test-server test-shared lint fixtures tidal-fixtures

help:
	@grep -E '^[a-z-]+:.*##' $(MAKEFILE_LIST) | sed 's/:.*##/\t/'

server: ## Run the resolver locally on :8080 (reads TIDAL_CLIENT_ID/SECRET from the environment)
	cd server && DOMAIN=localhost:8080 go run .

reverse: ## Forward the device's localhost:8080 to this machine's server
	adb reverse tcp:8080 tcp:8080

install: ## Build and install the debug APK
	./gradlew :androidApp:installDebug

share: ## Simulate sharing SHARE_URL from a streaming app
	adb shell "am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT '$(SHARE_URL)' -n $(PACKAGE)/.ShareActivity"

open: ## Simulate tapping a resolver link (explicit component: App Links are not verified for debug builds)
	adb shell "am start -a android.intent.action.VIEW -d https://$(LINK_HOST)/$(OPEN_PATH) -n $(PACKAGE)/.OpenLinkActivity"

logcat: ## Follow the app's log output
	adb logcat --pid=$$(adb shell pidof -s $(PACKAGE))

test: test-server test-shared ## Run all tests

test-server: ## Go tests with the race detector
	cd server && go test -race -count=1 ./...

test-shared: ## Kotlin tests for the link parser and the Android app's resolver logic
	./gradlew :shared:testAndroidHostTest :androidApp:testDebugUnitTest

lint: ## Static checks
	cd server && gofmt -l . && go vet ./...
	helm lint deploy/helm/onetune

fixtures: ## Re-record provider fixtures from the live APIs
	server/scripts/record-fixtures.sh

tidal-fixtures: ## Record real Tidal responses (needs TIDAL_CLIENT_ID/SECRET in the environment)
	server/scripts/record-tidal-fixtures.sh
