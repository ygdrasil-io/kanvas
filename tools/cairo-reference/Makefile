CFLAGS = -I/usr/local/include/cairo -I. -Wall -Wextra -O2
LDFLAGS = -L/usr/local/lib -lcairo -lm

render_scene: render_scene.c cJSON.c
	$(CC) $(CFLAGS) -o $@ render_scene.c cJSON.c $(LDFLAGS)

.PHONY: all clean test refs compare export full

all: render_scene refs

refs: render_scene
	mkdir -p out
	for json in scenes/*.json; do \
		name=$$(basename "$$json" .json); \
		./render_scene "$$json" "out/$$name.png"; \
	done
	@echo "Generated $$(ls out/*.png 2>/dev/null | wc -l | tr -d ' ') reference PNGs in out/"

test: refs
	python3 test_render.py

compare: refs
	python3 compare_references.py

export:
	@echo "From repo root, run:"
	@echo "  ./gradlew :gpu-renderer-scenes:exportCairoReferenceJson"
	@echo "Then run 'make refs' to render all JSON scenes."

clean:
	rm -f render_scene *.o
	rm -rf out/
