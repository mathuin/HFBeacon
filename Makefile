all:
	ant debug

install:
	ant debug

env:
	docker build -t mathuin/android .

debug:
	docker run -v $(HOME)/git/mathuin/HFBeacon/HFBeacon:/app/src mathuin/android ./build-hfbeacon.py debug

release:
	docker run -itv $(HOME)/git/mathuin/HFBeacon/HFBeacon:/app/src -v $(HOME)/keys:/keys mathuin/android ./build-hfbeacon.py release
