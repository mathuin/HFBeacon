env:
	docker build -t mathuin/android .

clean:
	docker run --rm -v $(HOME)/git/mathuin/HFBeacon/HFBeacon:/app/src mathuin/android /app/build-hfbeacon.py clean

test:
	docker run --rm -v $(HOME)/git/mathuin/HFBeacon/HFBeacon:/app/src -v $(HOME)/git/mathuin/HFBeacon/HFBeaconTest:/app/test mathuin/android /app/build-hfbeacon.py test

debug:
	docker run --rm -v $(HOME)/git/mathuin/HFBeacon/HFBeacon:/app/src mathuin/android /app/build-hfbeacon.py debug

release:
	docker run --rm -itv $(HOME)/git/mathuin/HFBeacon/HFBeacon:/app/src -v $(HOME)/keys:/keys mathuin/android /app/build-hfbeacon.py release

bash:
	docker run --rm -itv $(HOME)/git/mathuin/HFBeacon/HFBeacon:/app/src -v $(HOME)/git/mathuin/HFBeacon/HFBeaconTest:/app/test mathuin/android bash

