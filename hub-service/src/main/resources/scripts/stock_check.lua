
for i, key in ipairs(KEYS) do
    local requestQuantity = tonumber(ARGV[i])
    local currentStock =  redis.call('GET', key)

    if not currentStock then
        return -1
    end

    if tonumber(currentStock) < requestQuantity then
        return 0
    end

end

for i, key in ipairs(KEYS) do
    local requestQuantity = tonumber(ARGV[i])
    redis.call('DECRBY', key, requestQuantity)
end

return 1