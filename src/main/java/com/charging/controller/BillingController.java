@GetMapping("/list")
public Result list(@RequestParam int page,
                   @RequestParam int size) {
    return billService.list(page, size);
}