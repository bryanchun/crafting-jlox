build:
	@ gradle clean jar --no-daemon 1> /dev/null

lox: build
	@ # make lox					# prompt mode
	@ # make lox f=path/to/file	# source mode
	@ java -jar ./build/libs/crafting-lox-1.0-SNAPSHOT.jar $(f)

.PHONY: build lox
