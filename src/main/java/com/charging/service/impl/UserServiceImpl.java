@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public Result register(RegisterDTO dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(dto.getPassword());
        user.setBatteryCapacity(dto.getBatteryCapacity());

        userMapper.insert(user);
        return Result.success(user);
    }

    @Override
    public Result login(LoginDTO dto) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", dto.getUsername());
        User user = userMapper.selectOne(wrapper);

        if (user == null || !user.getPassword().equals(dto.getPassword())) {
            return Result.error("用户名或密码错误");
        }

        String token = JwtUtil.generateToken(user.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("token", token);

        return Result.success(data);
    }
}