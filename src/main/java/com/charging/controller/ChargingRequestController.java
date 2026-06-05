@RestController
@RequestMapping("/api/v1/request")
public class ChargingRequestController {

    @Autowired
    private ChargingRequestService service;

    @PostMapping("/submit")
    public Result submit(@RequestBody RequestDTO dto,
                         @RequestHeader("Authorization") String token) {

        Long userId = JwtUtil.parseToken(token.replace("Bearer ", ""));
        return service.submit(userId, dto);
    }

    @DeleteMapping("/cancel")
    public Result cancel(@RequestParam Long requestId) {
        return service.cancel(requestId);
    }

    @GetMapping("/status")
    public Result status(@RequestParam Long requestId) {
        return service.status(requestId);
    }
}