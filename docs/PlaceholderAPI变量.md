# PlaceholderAPI 变量

标识符固定为 `rookiecitystate`：

| 变量 | 返回值 |
| --- | --- |
| `%rookiecitystate_is_in_city_state%` | 是否属于城邦 |
| `%rookiecitystate_name%` | 城邦名 |
| `%rookiecitystate_member_signed_count%` | 成员签到次数 |
| `%rookiecitystate_member_position%` | 成员职位 |
| `%rookiecitystate_member_donated_gmoney%` | 成员已赞助的城邦币金额 |
| `%rookiecitystate_member_join_time%` | 加入时间 |
| `%rookiecitystate_ranking%` | 城邦排名 |
| `%rookiecitystate_owner%` | 城邦会长 |
| `%rookiecitystate_member_count%` | 成员数量 |
| `%rookiecitystate_max_member_count%` | 最大成员数量 |
| `%rookiecitystate_creation_time%` | 创建时间 |
| `%rookiecitystate_bank_gmoney%` | 城邦币储备 |
| `%rookiecitystate_online_member_count%` | 在线成员数 |
| `%rookiecitystate_guardian_title%` | 当前装备的永久灵兽称号；效果暂停时为空 |
| `%rookiecitystate_guardian_chat_prefix%` | 当前装备的永久灵兽聊天前缀；效果暂停时为空 |
| `%rookiecitystate_social_total_likes%` | 当前所属城邦的历史总赞 |
| `%rookiecitystate_social_7d_visitors%` | 当前所属城邦近 7 日独立合格访客数 |
| `%rookiecitystate_social_7d_likes%` | 当前所属城邦近 7 日点赞数 |
| `%rookiecitystate_social_hot_score%` | 当前所属城邦的社交热度 |
| `%rookiecitystate_social_hot_rank%` | 当前所属城邦的热门榜名次；未上榜为 0 |
| `%rookiecitystate_social_week_votes_remaining%` | 玩家本周剩余点赞票数；无城邦时也可用 |

EssentialsChat 标签使用相同后缀，例如 `<rookiecitystate_name>`、`<rookiecitystate_guardian_title>` 和
`<rookiecitystate_guardian_chat_prefix>` 和 `<rookiecitystate_social_hot_score>`。玩家级的
`<rookiecitystate_social_week_votes_remaining>` 在无城邦时也可用。内部 GUI 模板变量使用
`{city_state_*}`。
