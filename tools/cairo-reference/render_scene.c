#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <cairo.h>
#include "cJSON.h"

static void set_src(cairo_t *cr, cJSON *color) {
    double r = cJSON_GetObjectItem(color, "r")->valuedouble;
    double g = cJSON_GetObjectItem(color, "g")->valuedouble;
    double b = cJSON_GetObjectItem(color, "b")->valuedouble;
    double a = cJSON_GetObjectItem(color, "a")->valuedouble;
    cairo_set_source_rgba(cr, r, g, b, a);
}

static void do_clear(cairo_t *cr, int w, int h, cJSON *cmd) {
    cJSON *color = cJSON_GetObjectItem(cmd, "color");
    set_src(cr, color);
    cairo_rectangle(cr, 0, 0, w, h);
    cairo_fill(cr);
}

static void do_fill_rect(cairo_t *cr, cJSON *cmd) {
    cJSON *r = cJSON_GetObjectItem(cmd, "rect");
    double x = cJSON_GetObjectItem(r, "x")->valuedouble;
    double y = cJSON_GetObjectItem(r, "y")->valuedouble;
    double w = cJSON_GetObjectItem(r, "w")->valuedouble;
    double h = cJSON_GetObjectItem(r, "h")->valuedouble;
    cJSON *color = cJSON_GetObjectItem(cmd, "color");
    set_src(cr, color);
    cairo_rectangle(cr, x, y, w, h);
    cairo_fill(cr);
}

static void do_fill_rrect(cairo_t *cr, cJSON *cmd) {
    cJSON *r = cJSON_GetObjectItem(cmd, "rect");
    double x = cJSON_GetObjectItem(r, "x")->valuedouble;
    double y = cJSON_GetObjectItem(r, "y")->valuedouble;
    double w = cJSON_GetObjectItem(r, "w")->valuedouble;
    double h = cJSON_GetObjectItem(r, "h")->valuedouble;
    double rx = cJSON_GetObjectItem(cmd, "radius")->valuedouble;
    double ry = cJSON_GetObjectItem(cmd, "radius")->valuedouble;
    cJSON *color = cJSON_GetObjectItem(cmd, "color");
    set_src(cr, color);

    double degrees = M_PI / 180.0;
    cairo_new_sub_path(cr);
    cairo_arc(cr, x + w - rx, y + ry, rx, -90 * degrees, 0 * degrees);
    cairo_arc(cr, x + w - rx, y + h - ry, rx, 0 * degrees, 90 * degrees);
    cairo_arc(cr, x + rx, y + h - ry, rx, 90 * degrees, 180 * degrees);
    cairo_arc(cr, x + rx, y + ry, rx, 180 * degrees, 270 * degrees);
    cairo_close_path(cr);
    cairo_fill(cr);
}

static void do_linear_gradient(cairo_t *cr, cJSON *cmd) {
    cJSON *r = cJSON_GetObjectItem(cmd, "rect");
    double x = cJSON_GetObjectItem(r, "x")->valuedouble;
    double y = cJSON_GetObjectItem(r, "y")->valuedouble;
    double w = cJSON_GetObjectItem(r, "w")->valuedouble;
    double h = cJSON_GetObjectItem(r, "h")->valuedouble;

    cJSON *stops = cJSON_GetObjectItem(cmd, "stops");
    int num_stops = cJSON_GetArraySize(stops);

    double x1 = cJSON_GetObjectItem(cmd, "x1")->valuedouble;
    double y1 = cJSON_GetObjectItem(cmd, "y1")->valuedouble;
    double x2 = cJSON_GetObjectItem(cmd, "x2")->valuedouble;
    double y2 = cJSON_GetObjectItem(cmd, "y2")->valuedouble;

    cairo_pattern_t *pat = cairo_pattern_create_linear(x1, y1, x2, y2);
    for (int i = 0; i < num_stops; i++) {
        cJSON *stop = cJSON_GetArrayItem(stops, i);
        double pos = cJSON_GetObjectItem(stop, "pos")->valuedouble;
        cJSON *c = cJSON_GetObjectItem(stop, "color");
        double cr_ = cJSON_GetObjectItem(c, "r")->valuedouble;
        double cg = cJSON_GetObjectItem(c, "g")->valuedouble;
        double cb = cJSON_GetObjectItem(c, "b")->valuedouble;
        double ca = cJSON_GetObjectItem(c, "a")->valuedouble;
        cairo_pattern_add_color_stop_rgba(pat, pos, cr_, cg, cb, ca);
    }
    cairo_set_source(cr, pat);
    cairo_rectangle(cr, x, y, w, h);
    cairo_fill(cr);
    cairo_pattern_destroy(pat);
}

static void do_save_layer(cairo_t *cr, cJSON *cmd) {
    double alpha = 1.0;
    cJSON *a = cJSON_GetObjectItem(cmd, "groupAlpha");
    if (a) alpha = a->valuedouble;

    cairo_save(cr);
    cairo_push_group(cr);

    cJSON *children = cJSON_GetObjectItem(cmd, "commands");
    int n = cJSON_GetArraySize(children);
    for (int i = 0; i < n; i++) {
        cJSON *child = cJSON_GetArrayItem(children, i);
        cJSON *type = cJSON_GetObjectItem(child, "type");

        if (strcmp(type->valuestring, "FillRect") == 0)
            do_fill_rect(cr, child);
        else if (strcmp(type->valuestring, "FillRRect") == 0)
            do_fill_rrect(cr, child);
        else if (strcmp(type->valuestring, "LinearGradient") == 0)
            do_linear_gradient(cr, child);
        else if (strcmp(type->valuestring, "Clear") == 0)
            ; // skip clear inside saveLayer
        else
            fprintf(stderr, "WARNING: unsupported command inside SaveLayer: %s\n", type->valuestring);
    }

    cairo_pop_group_to_source(cr);
    cairo_paint_with_alpha(cr, alpha);
    cairo_restore(cr);
}

static void do_clip(cairo_t *cr, cJSON *cmd) {
    cJSON *r = cJSON_GetObjectItem(cmd, "rect");
    double x = cJSON_GetObjectItem(r, "x")->valuedouble;
    double y = cJSON_GetObjectItem(r, "y")->valuedouble;
    double w = cJSON_GetObjectItem(r, "w")->valuedouble;
    double h = cJSON_GetObjectItem(r, "h")->valuedouble;
    cairo_rectangle(cr, x, y, w, h);
    cairo_clip(cr);
}

static void render_commands(cairo_t *cr, int w, int h, cJSON *commands) {
    int n = cJSON_GetArraySize(commands);
    for (int i = 0; i < n; i++) {
        cJSON *cmd = cJSON_GetArrayItem(commands, i);
        cJSON *type = cJSON_GetObjectItem(cmd, "type");

        if (strcmp(type->valuestring, "Clear") == 0)
            do_clear(cr, w, h, cmd);
        else if (strcmp(type->valuestring, "FillRect") == 0)
            do_fill_rect(cr, cmd);
        else if (strcmp(type->valuestring, "FillRRect") == 0)
            do_fill_rrect(cr, cmd);
        else if (strcmp(type->valuestring, "LinearGradient") == 0)
            do_linear_gradient(cr, cmd);
        else if (strcmp(type->valuestring, "Clip") == 0)
            do_clip(cr, cmd);
        else if (strcmp(type->valuestring, "SaveLayer") == 0)
            do_save_layer(cr, cmd);
        else
            fprintf(stderr, "WARNING: unsupported command: %s\n", type->valuestring);
    }
}

int main(int argc, char **argv) {
    if (argc != 3) {
        fprintf(stderr, "Usage: %s <input.json> <output.png>\n", argv[0]);
        return 1;
    }

    const char *input_path = argv[1];
    const char *output_path = argv[2];

    FILE *f = fopen(input_path, "rb");
    if (!f) { perror("fopen"); return 1; }
    fseek(f, 0, SEEK_END);
    long len = ftell(f);
    fseek(f, 0, SEEK_SET);
    char *json_str = malloc(len + 1);
    fread(json_str, 1, len, f);
    fclose(f);
    json_str[len] = '\0';

    cJSON *root = cJSON_Parse(json_str);
    free(json_str);
    if (!root) {
        fprintf(stderr, "JSON parse error\n");
        return 1;
    }

    int w = cJSON_GetObjectItem(root, "width")->valueint;
    int h = cJSON_GetObjectItem(root, "height")->valueint;
    cJSON *commands = cJSON_GetObjectItem(root, "commands");

    cairo_surface_t *surface = cairo_image_surface_create(CAIRO_FORMAT_ARGB32, w, h);
    cairo_t *cr = cairo_create(surface);

    render_commands(cr, w, h, commands);

    cairo_destroy(cr);
    cairo_surface_write_to_png(surface, output_path);
    cairo_surface_destroy(surface);
    cJSON_Delete(root);

    printf("Rendered %dx%d -> %s\n", w, h, output_path);
    return 0;
}
