@Service
public class ChargingRequestServiceImpl implements ChargingRequestService {

    @Autowired
    private ChargingRequestMapper mapper;

    @Override
    public Result submit(Long userId, RequestDTO dto) {

        ChargingRequest req = new ChargingRequest();
        req.setUserId(userId);
        req.setMode(dto.getMode());
        req.setRequestedKwh(dto.getRequestedKwh());
        req.setStatus("WAITING");

        String prefix = dto.getMode().equals("FAST") ? "F" : "S";
        req.setQueueNumber(prefix + System.currentTimeMillis()%100);

        mapper.insert(req);

        return Result.success(req);
    }

    @Override
    public Result cancel(Long requestId) {
        ChargingRequest req = mapper.selectById(requestId);
        req.setStatus("CANCELLED");
        mapper.updateById(req);
        return Result.success();
    }

    @Override
    public Result status(Long requestId) {
        return Result.success(mapper.selectById(requestId));
    }
}