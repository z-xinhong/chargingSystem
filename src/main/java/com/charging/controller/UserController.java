@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Result register(@RequestBody RegisterDTO dto) {
        return userService.register(dto);
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginDTO dto) {
        return userService.login(dto);
    }
}