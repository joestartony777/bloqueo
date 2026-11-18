// Package blocking implements an explicit, reversible Windows hosts-file blocker.
// This is a beta defense against impulsive browsing, NOT a tamper-proof filter.
package blocking

import (
	"bufio"
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"time"
)

const (
	StartMarker     = "# BEGIN ILL_WIN_MANAGED_BLOCKS_V04"
	EndMarker       = "# END ILL_WIN_MANAGED_BLOCKS_V04"
	AdultSourceURL  = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts"
	MaxAdultEntries = 45000 // Windows hosts may degrade with very large files.
)

type Platform struct {
	ID, Name, Category string
	Domains            []string
}

// Domain-first profiles. Companion domains may provide shared functions and cause collateral blockage;
// allow user to toggle entire platforms, rather than silently blocking system-wide CDNs.
var platforms = []Platform{
	{"tiktok", "TikTok", "Social", []string{"tiktok.com", "tiktokcdn.com", "tiktokv.com", "musical.ly"}},
	{"instagram", "Instagram", "Social", []string{"instagram.com", "cdninstagram.com"}},
	{"facebook", "Facebook", "Social", []string{"facebook.com", "fb.com", "fb.watch", "m.facebook.com"}},
	{"x", "X / Twitter", "Social", []string{"x.com", "twitter.com", "t.co", "twimg.com"}},
	{"threads", "Threads", "Social", []string{"threads.net", "threads.com"}},
	{"snapchat", "Snapchat", "Social", []string{"snapchat.com"}},
	{"pinterest", "Pinterest", "Social", []string{"pinterest.com", "pinimg.com"}},
	{"reddit", "Reddit", "Social", []string{"reddit.com", "redd.it"}},
	{"bluesky", "Bluesky", "Social", []string{"bsky.app"}},
	{"linkedin", "LinkedIn", "Social", []string{"linkedin.com"}},
	{"tumblr", "Tumblr", "Social", []string{"tumblr.com"}},
	{"vk", "VK", "Social", []string{"vk.com", "vkontakte.ru"}},
	{"kwai", "Kwai", "Social", []string{"kwai.com", "kwai-video.com"}},
	{"likee", "Likee", "Social", []string{"likee.video"}},
	{"bereal", "BeReal", "Social", []string{"bere.al", "bereal.com"}},
	{"weibo", "Weibo", "Social", []string{"weibo.com"}},
	{"mastodon", "Mastodon", "Social", []string{"mastodon.social", "joinmastodon.org"}},
	{"wechat", "WeChat", "Mensajería", []string{"wechat.com", "weixin.qq.com"}},
	{"whatsapp", "WhatsApp Web", "Mensajería", []string{"web.whatsapp.com"}},
	{"telegram", "Telegram Web", "Mensajería", []string{"web.telegram.org"}},
	{"discord", "Discord", "Mensajería", []string{"discord.com", "discord.gg"}},
	{"signal", "Signal", "Mensajería", []string{"signal.org"}},
	{"messenger", "Messenger", "Mensajería", []string{"messenger.com"}},
	{"youtube", "YouTube", "Vídeo", []string{"youtube.com", "youtu.be", "youtube-nocookie.com"}},
	{"twitch", "Twitch", "Vídeo", []string{"twitch.tv"}},
	{"kick", "Kick", "Vídeo", []string{"kick.com"}},
	{"dailymotion", "Dailymotion", "Vídeo", []string{"dailymotion.com"}},
	{"vimeo", "Vimeo", "Vídeo", []string{"vimeo.com"}},
	{"rumble", "Rumble", "Vídeo", []string{"rumble.com"}},
	{"netflix", "Netflix", "Streaming", []string{"netflix.com"}},
	{"disney", "Disney+", "Streaming", []string{"disneyplus.com"}},
	{"primevideo", "Prime Video", "Streaming", []string{"primevideo.com"}},
	{"crunchyroll", "Crunchyroll", "Streaming", []string{"crunchyroll.com"}},
	{"hulu", "Hulu", "Streaming", []string{"hulu.com"}},
	{"spotify", "Spotify Web", "Streaming", []string{"open.spotify.com"}},
	{"steam", "Steam", "Juegos", []string{"store.steampowered.com", "steamcommunity.com"}},
	{"epicgames", "Epic Games", "Juegos", []string{"store.epicgames.com"}},
	{"roblox", "Roblox", "Juegos", []string{"roblox.com"}},
	{"chess", "Chess.com", "Juegos", []string{"chess.com"}},
	{"poki", "Poki", "Juegos", []string{"poki.com"}},
	{"crazygames", "CrazyGames", "Juegos", []string{"crazygames.com"}},
	{"9gag", "9GAG", "Entretenimiento", []string{"9gag.com"}},
	{"imgur", "Imgur", "Entretenimiento", []string{"imgur.com"}},
	{"tinder", "Tinder", "Entretenimiento", []string{"tinder.com"}},
	{"bumble", "Bumble", "Entretenimiento", []string{"bumble.com"}},
	{"lemon8", "Lemon8", "Social", []string{"lemon8-app.com", "lemon8.com"}},
	{"yubo", "Yubo", "Social", []string{"yubo.live"}},
	{"nextdoor", "Nextdoor", "Social", []string{"nextdoor.com"}},
	{"quora", "Quora", "Social", []string{"quora.com"}},
	{"mewe", "MeWe", "Social", []string{"mewe.com"}},
	{"rednote", "RedNote / Xiaohongshu", "Social", []string{"xiaohongshu.com", "rednote.com"}},
	{"line", "LINE", "Mensajería", []string{"line.me"}},
	{"bilibili", "Bilibili", "Vídeo", []string{"bilibili.com"}},
	{"douyin", "Douyin", "Vídeo", []string{"douyin.com"}},
	{"kuaishou", "Kuaishou", "Vídeo", []string{"kuaishou.com"}},
	{"flickr", "Flickr", "Social", []string{"flickr.com"}},
	{"vsco", "VSCO", "Social", []string{"vsco.co"}},
	{"characterai", "Character.AI", "Entretenimiento", []string{"character.ai"}},
	{"onlyfans", "OnlyFans", "Adultos", []string{"onlyfans.com"}},
}

func Platforms() []Platform {
	out := make([]Platform, len(platforms))
	copy(out, platforms)
	return out
}
func AllIDs() []string {
	o := make([]string, 0, len(platforms))
	for _, p := range platforms {
		o = append(o, p.ID)
	}
	return o
}

type Policy struct {
	Configured      bool     `json:"configured"`
	Enabled         bool     `json:"enabled"`
	Selected        []string `json:"selected"`
	Adult           bool     `json:"adult"`
	ScheduleEnabled bool     `json:"schedule_enabled"`
	ScheduleStart   string   `json:"schedule_start"`
	ScheduleEnd     string   `json:"schedule_end"`
	ScheduleMode    string   `json:"schedule_mode,omitempty"` // always, weekdays, custom; empty retains legacy daily scheduling
	ScheduleDays    []int    `json:"schedule_days,omitempty"` // 0=domingo .. 6=sábado for custom
	// Future unlock; once the time arrives all blocking is lifted, never by an impulsive immediate click.
	RequestedDisableAt string `json:"requested_disable_at,omitempty"`
	Mode               string `json:"mode"`
	Testing            bool   `json:"testing"`
}

func Default() Policy {
	return Policy{Selected: AllIDs(), ScheduleStart: "08:00", ScheduleEnd: "22:00", ScheduleMode: "always", Mode: "all", Testing: true}
}
func (p Policy) PlannedCount() int { return len(p.Selected) }
func (p Policy) DomainSet(adultDomains []string) []string {
	selected := make(map[string]bool, len(p.Selected))
	for _, id := range p.Selected {
		selected[id] = true
	}
	domains := map[string]bool{}
	for _, pf := range platforms {
		if selected[pf.ID] {
			for _, d := range pf.Domains {
				if ValidDomain(d) {
					domains[d] = true
					if !strings.Contains(d, "web.") && !strings.Contains(d, "open.") && !strings.Contains(d, "store.") {
						domains["www."+d] = true
					}
				}
			}
		}
	}
	if p.Adult {
		for _, d := range adultDomains {
			if ValidDomain(d) {
				domains[d] = true
			}
		}
	}
	out := make([]string, 0, len(domains))
	for k := range domains {
		out = append(out, k)
	}
	sort.Strings(out)
	return out
}
func parseHHMM(s string) (int, bool) {
	var h, m int
	if _, e := fmt.Sscanf(s, "%d:%d", &h, &m); e != nil || h < 0 || h > 23 || m < 0 || m > 59 {
		return 0, false
	}
	return h*60 + m, true
}

// scheduleDay reports whether a weekday is selected. Empty means every day for
// legacy policies and avoids changing already-configured users' rules.
func (p Policy) scheduleDay(day time.Weekday) bool {
	if len(p.ScheduleDays) == 0 {
		return true
	}
	for _, v := range p.ScheduleDays {
		if v == int(day) {
			return true
		}
	}
	return false
}
func (p Policy) ActiveAt(t time.Time) bool {
	if !p.Enabled {
		return false
	}
	if p.RequestedDisableAt != "" {
		at, e := time.Parse(time.RFC3339, p.RequestedDisableAt)
		if e == nil && !t.Before(at) {
			return false
		}
	}
	if p.ScheduleMode == "weekdays" {
		return t.Weekday() >= time.Monday && t.Weekday() <= time.Friday
	}
	if !p.ScheduleEnabled {
		return true
	}
	s, ok1 := parseHHMM(p.ScheduleStart)
	e, ok2 := parseHHMM(p.ScheduleEnd)
	if !ok1 || !ok2 || s == e {
		return false
	}
	now := t.Hour()*60 + t.Minute()
	if s < e {
		return p.scheduleDay(t.Weekday()) && now >= s && now < e
	}
	// An overnight interval belongs to the day on which it started.
	if now >= s {
		return p.scheduleDay(t.Weekday())
	}
	previous := t.AddDate(0, 0, -1)
	return now < e && p.scheduleDay(previous.Weekday())
}
func (p Policy) Validate() error {
	if len(p.Selected) > 100 {
		return errors.New("demasiadas selecciones")
	}
	set := map[string]bool{}
	for _, pf := range platforms {
		set[pf.ID] = true
	}
	for _, id := range p.Selected {
		if !set[id] {
			return fmt.Errorf("plataforma desconocida: %s", id)
		}
	}
	if p.ScheduleMode != "" && p.ScheduleMode != "always" && p.ScheduleMode != "weekdays" && p.ScheduleMode != "custom" {
		return errors.New("modo de horario desconocido")
	}
	if p.ScheduleMode == "custom" && len(p.ScheduleDays) == 0 {
		return errors.New("selecciona al menos un día")
	}
	daysSeen := map[int]bool{}
	for _, day := range p.ScheduleDays {
		if day < 0 || day > 6 || daysSeen[day] {
			return errors.New("día de horario inválido")
		}
		daysSeen[day] = true
	}
	if p.ScheduleEnabled && p.ScheduleMode != "weekdays" {
		a, x := parseHHMM(p.ScheduleStart)
		b, y := parseHHMM(p.ScheduleEnd)
		if !x || !y || a == b {
			return errors.New("horario invalido")
		}
	}
	if p.RequestedDisableAt != "" {
		if _, e := time.Parse(time.RFC3339, p.RequestedDisableAt); e != nil {
			return fmt.Errorf("fecha: %w", e)
		}
	}
	return nil
}

var domainRE = regexp.MustCompile(`^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])$`)

func ValidDomain(s string) bool {
	if len(s) < 4 || len(s) > 253 || net.ParseIP(s) != nil || strings.Contains(s, "..") {
		return false
	}
	return domainRE.MatchString(s) && strings.Contains(s, ".")
}
func ReadPolicy(path string) (Policy, error) {
	b, e := os.ReadFile(path)
	if e != nil {
		return Default(), e
	}
	var p Policy
	if e = json.Unmarshal(b, &p); e != nil {
		return Default(), e
	}
	return p, p.Validate()
}
func SavePolicy(path string, p Policy) error {
	if e := p.Validate(); e != nil {
		return e
	}
	b, _ := json.MarshalIndent(p, "", "  ")
	return safeWrite(path, b, 0600)
}
func safeWrite(path string, b []byte, mode os.FileMode) error {
	if e := os.MkdirAll(filepath.Dir(path), 0700); e != nil {
		return e
	}
	tmp, e := os.CreateTemp(filepath.Dir(path), "illwin-*.tmp")
	if e != nil {
		return e
	}
	defer os.Remove(tmp.Name())
	if _, e = tmp.Write(b); e != nil {
		tmp.Close()
		return e
	}
	if e = tmp.Sync(); e != nil {
		tmp.Close()
		return e
	}
	if e = tmp.Close(); e != nil {
		return e
	}
	_ = os.Chmod(tmp.Name(), mode)
	if e = os.Rename(tmp.Name(), path); e != nil { // Windows may reject replacing a destination.
		return os.WriteFile(path, b, mode)
	}
	return nil
}
func ManagedText(existing string, domains []string) (string, error) {
	// Refuse to operate if a user-modified managed section is malformed.
	b := strings.Index(existing, StartMarker)
	e := strings.Index(existing, EndMarker)
	if (b < 0) != (e < 0) || (b >= 0 && e < b) {
		return "", errors.New("seccion gestionada de hosts dañada; no modificar automaticamente")
	}
	if b >= 0 {
		end := e + len(EndMarker)
		existing = existing[:b] + existing[end:]
	}
	existing = strings.TrimRight(existing, "\r\n")
	if len(domains) == 0 {
		return existing + "\r\n", nil
	}
	var sb strings.Builder
	sb.WriteString(existing + "\r\n" + StartMarker + "\r\n")
	for _, d := range domains {
		if !ValidDomain(d) {
			return "", fmt.Errorf("dominio inseguro %s", d)
		}
		sb.WriteString("0.0.0.0 " + d + "\r\n")
		sb.WriteString(":: " + d + "\r\n")
	}
	sb.WriteString(EndMarker + "\r\n")
	return sb.String(), nil
}
func ApplyHosts(hostsFile string, domains []string) (int, error) {
	current, e := os.ReadFile(hostsFile)
	if e != nil {
		return 0, e
	}
	if len(domains) == 0 && !bytes.Contains(current, []byte(StartMarker)) {
		return 0, nil // Never alter unrelated hosts content just to disable an inactive block.
	}
	content, e := ManagedText(string(current), domains)
	if e != nil {
		return 0, e
	}
	if bytes.Equal(current, []byte(content)) {
		return len(domains), nil
	}
	backup := hostsFile + ".illwin.backup"
	if _, err := os.Stat(backup); os.IsNotExist(err) {
		if err = os.WriteFile(backup, current, 0600); err != nil {
			return 0, fmt.Errorf("respaldo: %w", err)
		}
	}
	if e := safeWrite(hostsFile, []byte(content), 0644); e != nil {
		return 0, e
	}
	confirmed, readErr := os.ReadFile(hostsFile)
	if readErr != nil || !bytes.Equal(confirmed, []byte(content)) {
		return 0, errors.New("Windows no conservó el archivo hosts solicitado")
	}
	return len(domains), nil
}
func ParseHostsList(r io.Reader, limit int) ([]string, error) {
	scanner := bufio.NewScanner(io.LimitReader(r, 15<<20))
	scanner.Buffer(make([]byte, 16384), 1<<20)
	seen := map[string]bool{}
	out := make([]string, 0, 1024)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) < 2 {
			continue
		}
		if fields[0] != "0.0.0.0" && fields[0] != "127.0.0.1" {
			continue
		}
		for _, d := range fields[1:] {
			d = strings.ToLower(d)
			if d == "localhost" || !ValidDomain(d) || seen[d] {
				continue
			}
			seen[d] = true
			out = append(out, d)
			if len(out) >= limit {
				return out, nil
			}
		}
	}
	return out, scanner.Err()
}
func AdultDomains(cacheFile string, client *http.Client) ([]string, string, error) {
	// Uses a maintained public hostlist; never claim total coverage.
	if st, e := os.Stat(cacheFile); e == nil && time.Since(st.ModTime()) < 24*time.Hour {
		f, e := os.Open(cacheFile)
		if e == nil {
			defer f.Close()
			d, e := ParseHostsList(f, MaxAdultEntries)
			if e == nil && len(d) > 300 {
				return d, "caché", nil
			}
		}
	}
	req, e := http.NewRequest("GET", AdultSourceURL, nil)
	if e == nil {
		var resp *http.Response
		resp, e = client.Do(req)
		if e == nil {
			defer resp.Body.Close()
			if resp.StatusCode == 200 {
				content, e2 := io.ReadAll(io.LimitReader(resp.Body, 15<<20))
				if e2 == nil {
					list, e2 := ParseHostsList(bytes.NewReader(content), MaxAdultEntries)
					if e2 == nil && len(list) > 300 {
						_ = safeWrite(cacheFile, content, 0600)
						return list, "StevenBlack", nil
					}
				}
			} else {
				e = fmt.Errorf("HTTP %d", resp.StatusCode)
			}
		}
	}
	// Prefer existing cache even if old; no hidden fallback to an inadequate 20-domain list.
	if f, err := os.Open(cacheFile); err == nil {
		defer f.Close()
		list, x := ParseHostsList(f, MaxAdultEntries)
		if x == nil && len(list) > 300 {
			return list, "caché antigua", nil
		}
	}
	return nil, "", fmt.Errorf("no disponible lista de contenido adulto: %v", e)
}
